package dev.mintychochip.api.rewards;

import java.util.UUID;

/** Immutable ranked player score. */
public record LeaderboardEntry(UUID playerId, int rank, long score) {

  public LeaderboardEntry {
    java.util.Objects.requireNonNull(playerId, "playerId");
    if (rank <= 0) {
      throw new IllegalArgumentException("rank must be positive");
    }
    if (score < 0) {
      throw new IllegalArgumentException("score must not be negative");
    }
  }
}
