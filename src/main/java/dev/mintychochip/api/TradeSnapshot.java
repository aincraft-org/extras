package dev.mintychochip.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable view of a transient two-player trade. */
public final class TradeSnapshot {
  private final UUID tradeId;
  private final UUID firstPlayerId;
  private final UUID secondPlayerId;
  private final long firstOfferVersion;
  private final long secondOfferVersion;
  private final boolean firstConfirmed;
  private final boolean secondConfirmed;

  public TradeSnapshot(
      UUID tradeId,
      UUID firstPlayerId,
      UUID secondPlayerId,
      long firstOfferVersion,
      long secondOfferVersion,
      boolean firstConfirmed,
      boolean secondConfirmed) {
    this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
    this.firstPlayerId = Objects.requireNonNull(firstPlayerId, "firstPlayerId");
    this.secondPlayerId = Objects.requireNonNull(secondPlayerId, "secondPlayerId");
    if (firstPlayerId.equals(secondPlayerId)) {
      throw new IllegalArgumentException("trade participants must differ");
    }
    this.firstOfferVersion = firstOfferVersion;
    this.secondOfferVersion = secondOfferVersion;
    this.firstConfirmed = firstConfirmed;
    this.secondConfirmed = secondConfirmed;
  }

  public UUID tradeId() {
    return tradeId;
  }

  public UUID firstPlayerId() {
    return firstPlayerId;
  }

  public UUID secondPlayerId() {
    return secondPlayerId;
  }

  public long offerVersionOf(UUID playerId) {
    if (firstPlayerId.equals(playerId)) {
      return firstOfferVersion;
    }
    if (secondPlayerId.equals(playerId)) {
      return secondOfferVersion;
    }
    throw new IllegalArgumentException("player is not a trade participant");
  }

  public boolean confirmed(UUID playerId) {
    if (firstPlayerId.equals(playerId)) {
      return firstConfirmed;
    }
    if (secondPlayerId.equals(playerId)) {
      return secondConfirmed;
    }
    throw new IllegalArgumentException("player is not a trade participant");
  }

  public boolean includes(UUID playerId) {
    return firstPlayerId.equals(playerId) || secondPlayerId.equals(playerId);
  }
}
