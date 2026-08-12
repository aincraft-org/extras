package dev.mintychochip.core;

import dev.mintychochip.api.FriendRequest;
import dev.mintychochip.api.FriendResult;
import dev.mintychochip.api.FriendService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default Bukkit-free {@link FriendService} backed by a {@link FriendRepository}.
 *
 * <p>Every mutation is guarded by a single internal lock so invariants that require a
 * check-then-act (duplicate requests, "already friends") are atomic with respect to concurrent
 * callers. Reads are lock-free.
 */
public final class DefaultFriendService implements FriendService {

  private final FriendRepository repository;
  private final Clock clock;
  private final Object mutationLock = new Object();

  public DefaultFriendService(FriendRepository repository) {
    this(repository, Clock.systemUTC());
  }

  public DefaultFriendService(FriendRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public FriendResult sendRequest(UUID requesterId, UUID targetId) {
    Objects.requireNonNull(requesterId, "requesterId");
    Objects.requireNonNull(targetId, "targetId");
    if (requesterId.equals(targetId)) {
      return FriendResult.SELF_REQUEST;
    }
    synchronized (mutationLock) {
      if (repository.findFriendship(requesterId, targetId).isPresent()) {
        return FriendResult.ALREADY_FRIENDS;
      }
      // A pending request in either direction already covers the pair.
      boolean pending =
          repository.findRequest(requesterId, targetId).isPresent()
              || repository.findRequest(targetId, requesterId).isPresent();
      if (pending) {
        return FriendResult.REQUEST_EXISTS;
      }
      repository.upsertRequest(requesterId, targetId, clock.instant());
      return FriendResult.SUCCESS;
    }
  }

  @Override
  public FriendResult acceptRequest(UUID recipientId, UUID requesterId) {
    Objects.requireNonNull(recipientId, "recipientId");
    Objects.requireNonNull(requesterId, "requesterId");
    synchronized (mutationLock) {
      if (repository.findFriendship(recipientId, requesterId).isPresent()) {
        return FriendResult.ALREADY_FRIENDS;
      }
      if (repository.findRequest(requesterId, recipientId).isEmpty()) {
        return FriendResult.NO_REQUEST;
      }
      Instant now = clock.instant();
      repository.deleteRequest(requesterId, recipientId);
      repository.addFriendship(recipientId, requesterId, now);
      return FriendResult.SUCCESS;
    }
  }

  @Override
  public FriendResult declineRequest(UUID recipientId, UUID requesterId) {
    Objects.requireNonNull(recipientId, "recipientId");
    Objects.requireNonNull(requesterId, "requesterId");
    synchronized (mutationLock) {
      if (repository.findRequest(requesterId, recipientId).isEmpty()) {
        return FriendResult.NO_REQUEST;
      }
      repository.deleteRequest(requesterId, recipientId);
      return FriendResult.SUCCESS;
    }
  }

  @Override
  public FriendResult cancelRequest(UUID requesterId, UUID targetId) {
    Objects.requireNonNull(requesterId, "requesterId");
    Objects.requireNonNull(targetId, "targetId");
    synchronized (mutationLock) {
      if (repository.findRequest(requesterId, targetId).isEmpty()) {
        return FriendResult.NO_REQUEST;
      }
      repository.deleteRequest(requesterId, targetId);
      return FriendResult.SUCCESS;
    }
  }

  @Override
  public FriendResult removeFriend(UUID actorId, UUID targetId) {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(targetId, "targetId");
    synchronized (mutationLock) {
      if (repository.findFriendship(actorId, targetId).isEmpty()) {
        return FriendResult.NOT_FRIENDS;
      }
      repository.deleteFriendship(actorId, targetId);
      return FriendResult.SUCCESS;
    }
  }

  @Override
  public boolean areFriends(UUID a, UUID b) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    return repository.findFriendship(a, b).isPresent();
  }

  @Override
  public List<FriendRequest> incomingRequests(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return List.copyOf(repository.findIncoming(playerId));
  }

  @Override
  public List<FriendRequest> outgoingRequests(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return List.copyOf(repository.findOutgoing(playerId));
  }

  @Override
  public List<UUID> friendIdsOf(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return List.copyOf(repository.findFriendIds(playerId));
  }

  /** Closes the backing store. */
  public void close() {
    repository.close();
  }
}
