package dev.mintychochip.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a player party.
 *
 * <p>A party has exactly one {@code leaderId} and an ordered (by join time) membership list. The
 * leader is also a member. Read views returned by {@link PartyService} are always of this type; no
 * reference escapes into mutable state.
 */
public final class Party {

  private final UUID partyId;
  private final String name;
  private final UUID leaderId;
  private final List<UUID> memberIds;
  private final Instant createdAt;

  public Party(UUID partyId, String name, UUID leaderId, List<UUID> memberIds, Instant createdAt) {
    this.partyId = Objects.requireNonNull(partyId, "partyId");
    this.name = name;
    this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
    this.memberIds =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(memberIds, "memberIds")));
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public UUID partyId() {
    return partyId;
  }

  /** The party name, or {@code null} when the party was created unnamed. */
  public String name() {
    return name;
  }

  public UUID leaderId() {
    return leaderId;
  }

  public List<UUID> memberIds() {
    return memberIds;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public boolean hasMember(UUID playerId) {
    return memberIds.contains(playerId);
  }

  public boolean isLeader(UUID playerId) {
    return leaderId.equals(playerId);
  }

  public int size() {
    return memberIds.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Party that)) {
      return false;
    }
    return partyId.equals(that.partyId)
        && Objects.equals(name, that.name)
        && leaderId.equals(that.leaderId)
        && memberIds.equals(that.memberIds)
        && createdAt.equals(that.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(partyId, name, leaderId, memberIds, createdAt);
  }

  @Override
  public String toString() {
    return "Party{partyId="
        + partyId
        + ", name="
        + name
        + ", leaderId="
        + leaderId
        + ", memberIds="
        + memberIds
        + ", createdAt="
        + createdAt
        + '}';
  }
}
