package dev.mintychochip.core;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;
import dev.mintychochip.api.PartyResult;
import dev.mintychochip.api.PartyService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default Bukkit-free {@link PartyService} backed by a {@link PartyRepository}.
 *
 * <p>Every mutation is guarded by a single internal lock so invariants that require a
 * check-then-act (the member cap, "already in party", leadership) are atomic with respect to
 * concurrent callers. Reads are lock-free and hit the cache first, then the store.
 */
public final class DefaultPartyService implements PartyService {

  private static final Duration DEFAULT_INVITE_TTL = Duration.ofSeconds(60);
  private static final int DEFAULT_SIZE_LIMIT = 4;
  private static final int MAX_NAME_LENGTH = 32;

  private final PartyRepository repository;
  private final Clock clock;
  private final Duration inviteTtl;
  private final int sizeLimit;
  private final Object mutationLock = new Object();
  private final ConcurrentMap<UUID, Party> cache = new ConcurrentHashMap<>();

  public DefaultPartyService(PartyRepository repository) {
    this(repository, Clock.systemUTC(), DEFAULT_INVITE_TTL, DEFAULT_SIZE_LIMIT);
  }

  public DefaultPartyService(
      PartyRepository repository, Clock clock, Duration inviteTtl, int sizeLimit) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.inviteTtl = Objects.requireNonNull(inviteTtl, "inviteTtl");
    if (sizeLimit < 1) {
      throw new IllegalArgumentException("sizeLimit must be >= 1");
    }
    this.sizeLimit = sizeLimit;
  }

  @Override
  public PartyResult createParty(UUID playerId, String name) {
    Objects.requireNonNull(playerId, "playerId");
    // null name means an unnamed party; only non-null names are validated.
    if (name != null && normalizeName(name) == null) {
      return PartyResult.INVALID_NAME;
    }
    String normalized = name == null ? null : normalizeName(name);
    synchronized (mutationLock) {
      if (partyOf(playerId).isPresent()) {
        return PartyResult.ALREADY_IN_PARTY;
      }
      UUID partyId = UUID.randomUUID();
      Instant now = clock.instant();
      Party party = new Party(partyId, normalized, playerId, List.of(playerId), now);
      repository.createParty(partyId, normalized, playerId, now);
      cache.put(playerId, party);
      return PartyResult.SUCCESS;
    }
  }

  @Override
  public PartyResult invite(UUID actorId, UUID targetId) {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(targetId, "targetId");
    if (actorId.equals(targetId)) {
      return PartyResult.SELF_INVITE;
    }
    synchronized (mutationLock) {
      Optional<Party> party = partyOf(actorId);
      if (party.isEmpty()) {
        return PartyResult.NOT_IN_PARTY;
      }
      if (partyOf(targetId).isPresent()) {
        return PartyResult.TARGET_IN_PARTY;
      }
      Party current = party.get();
      if (current.size() >= sizeLimit) {
        return PartyResult.PARTY_FULL;
      }
      Instant expiry = clock.instant().plus(inviteTtl);
      boolean already =
          repository.findInvite(current.partyId(), targetId, clock.instant()).isPresent();
      repository.upsertInvite(current.partyId(), targetId, actorId, expiry);
      return already ? PartyResult.ALREADY_INVITED : PartyResult.SUCCESS;
    }
  }

  @Override
  public PartyResult acceptInvite(UUID playerId, UUID partyId) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(partyId, "partyId");
    synchronized (mutationLock) {
      if (partyOf(playerId).isPresent()) {
        return PartyResult.ALREADY_IN_PARTY;
      }
      if (repository.findInvite(partyId, playerId, clock.instant()).isEmpty()) {
        return PartyResult.NO_INVITE;
      }
      Party party = repository.findById(partyId).orElse(null);
      if (party == null) {
        return PartyResult.NO_INVITE; // party disbanded between invite and accept
      }
      if (party.size() >= sizeLimit) {
        return PartyResult.PARTY_FULL;
      }
      Instant joinedAt = clock.instant();
      repository.acceptInvite(partyId, playerId, joinedAt);
      Party refreshed = repository.findById(partyId).orElseThrow();
      for (UUID memberId : refreshed.memberIds()) {
        cache.put(memberId, refreshed);
      }
      return PartyResult.SUCCESS;
    }
  }

  @Override
  public PartyResult declineInvite(UUID playerId, UUID partyId) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(partyId, "partyId");
    synchronized (mutationLock) {
      if (repository.findInvite(partyId, playerId, clock.instant()).isEmpty()) {
        return PartyResult.NO_INVITE;
      }
      repository.deleteInvite(partyId, playerId);
      return PartyResult.SUCCESS;
    }
  }

  @Override
  public PartyResult leaveParty(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    synchronized (mutationLock) {
      Party current = partyOf(playerId).orElse(null);
      if (current == null) {
        return PartyResult.NOT_IN_PARTY;
      }
      if (current.isLeader(playerId)) {
        leaveAsLeader(current, playerId);
      } else {
        repository.removeMember(current.partyId(), playerId);
        Party refreshed = repository.findById(current.partyId()).orElseThrow();
        hitCache(refreshed);
        cache.remove(playerId);
      }
      return PartyResult.SUCCESS;
    }
  }

  /** Called with {@link #mutationLock} held. */
  private void leaveAsLeader(Party party, UUID leaderId) {
    List<UUID> remaining = new ArrayList<>(party.memberIds());
    remaining.remove(leaderId);
    if (remaining.isEmpty()) {
      repository.deleteParty(party.partyId());
      cache.remove(leaderId);
      return;
    }
    UUID newLeader = remaining.get(0);
    repository.leaderLeaves(party.partyId(), leaderId, newLeader);
    Party refreshed = repository.findById(party.partyId()).orElseThrow();
    hitCache(refreshed);
    cache.remove(leaderId);
  }

  @Override
  public PartyResult kick(UUID actorId, UUID targetId) {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(targetId, "targetId");
    if (actorId.equals(targetId)) {
      return PartyResult.SELF_KICK;
    }
    synchronized (mutationLock) {
      Party party = partyOf(actorId).orElse(null);
      if (party == null) {
        return PartyResult.NOT_IN_PARTY;
      }
      if (!party.isLeader(actorId)) {
        return PartyResult.NOT_LEADER;
      }
      if (!party.hasMember(targetId)) {
        return PartyResult.NOT_A_MEMBER;
      }
      repository.removeMember(party.partyId(), targetId);
      Party refreshed = repository.findById(party.partyId()).orElseThrow();
      hitCache(refreshed);
      cache.remove(targetId);
      return PartyResult.SUCCESS;
    }
  }

  @Override
  public PartyResult disband(UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    synchronized (mutationLock) {
      Party party = partyOf(actorId).orElse(null);
      if (party == null) {
        return PartyResult.NOT_IN_PARTY;
      }
      if (!party.isLeader(actorId)) {
        return PartyResult.NOT_LEADER;
      }
      repository.deleteParty(party.partyId());
      for (UUID memberId : party.memberIds()) {
        cache.remove(memberId);
      }
      return PartyResult.SUCCESS;
    }
  }

  @Override
  public PartyResult transferLeadership(UUID actorId, UUID targetId) {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(targetId, "targetId");
    synchronized (mutationLock) {
      Party party = partyOf(actorId).orElse(null);
      if (party == null) {
        return PartyResult.NOT_IN_PARTY;
      }
      if (!party.isLeader(actorId)) {
        return PartyResult.NOT_LEADER;
      }
      if (!party.hasMember(targetId)) {
        return PartyResult.NOT_A_MEMBER;
      }
      repository.setLeader(party.partyId(), targetId);
      Party refreshed = repository.findById(party.partyId()).orElseThrow();
      hitCache(refreshed);
      return PartyResult.SUCCESS;
    }
  }

  @Override
  public Optional<Party> partyOf(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    Party cached = cache.get(playerId);
    if (cached != null) {
      return Optional.of(cached);
    }
    return repository
        .findByMember(playerId)
        .map(
            party -> {
              hitCache(party);
              return party;
            });
  }

  /** Refreshes the member-to-party cache for every member of {@code party}. */
  private void hitCache(Party party) {
    for (UUID memberId : party.memberIds()) {
      cache.put(memberId, party);
    }
  }

  @Override
  public List<PartyInvite> pendingInvitations(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return repository.findPendingInvites(playerId, clock.instant());
  }

  /** Normalizes a party name: trims, rejects blank/oversized/control names. */
  private static String normalizeName(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      return null;
    }
    for (int i = 0; i < trimmed.length(); i++) {
      if (Character.isISOControl(trimmed.charAt(i))) {
        return null;
      }
    }
    return trimmed;
  }

  /** Evicts the player's cached party after they log off. */
  public void evict(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    cache.remove(playerId);
  }

  /**
   * Handles a player logging off: membership and the party persist. If the leaving player leads the
   * party and another member remains, leadership auto-transfers to the longest-standing remaining
   * member via {@code setLeader} (the leaving leader stays a member and keeps their seat when they
   * return). Pending invites of the leaving leader move to the new leader so they do not dangle. A
   * single-member party is kept. Evicts the player from the cache; a non-leader is only evicted.
   */
  public void logout(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    synchronized (mutationLock) {
      Party current = partyOf(playerId).orElse(null);
      if (current == null) {
        return;
      }
      if (!current.isLeader(playerId)) {
        evict(playerId);
        return;
      }
      List<UUID> remaining = new ArrayList<>(current.memberIds());
      remaining.remove(playerId);
      if (remaining.isEmpty()) {
        // Solo leader logs off: party stays, leadership unchanged.
        evict(playerId);
        return;
      }
      UUID newLeader = remaining.get(0);
      repository.setLeader(current.partyId(), newLeader);
      for (PartyInvite invite : repository.findPendingInvitesUnbounded(current.partyId())) {
        if (invite.inviterId().equals(playerId)) {
          repository.reassignInviteInviter(current.partyId(), playerId, newLeader);
        }
      }
      Party refreshed = repository.findById(current.partyId()).orElseThrow();
      hitCache(refreshed);
      evict(playerId);
    }
  }

  /** Closes the backing store and drops the in-memory cache. */
  public void close() {
    repository.close();
    cache.clear();
  }
}
