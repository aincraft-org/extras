package dev.mintychochip.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of a pending friend request.
 *
 * <p>A request is directional: {@code requesterId} sent it to {@code targetId}. Only the target may
 * accept or decline it; only the requester may cancel it. Requests do not expire and persist across
 * restarts until acted upon.
 */
public final class FriendRequest {

  private final UUID requesterId;
  private final UUID targetId;
  private final Instant createdAt;

  public FriendRequest(UUID requesterId, UUID targetId, Instant createdAt) {
    this.requesterId = Objects.requireNonNull(requesterId, "requesterId");
    this.targetId = Objects.requireNonNull(targetId, "targetId");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /** The player who sent the request. */
  public UUID requesterId() {
    return requesterId;
  }

  /** The player who receives the request. */
  public UUID targetId() {
    return targetId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FriendRequest that)) {
      return false;
    }
    return requesterId.equals(that.requesterId)
        && targetId.equals(that.targetId)
        && createdAt.equals(that.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requesterId, targetId, createdAt);
  }

  @Override
  public String toString() {
    return "FriendRequest{requesterId="
        + requesterId
        + ", targetId="
        + targetId
        + ", createdAt="
        + createdAt
        + '}';
  }
}
