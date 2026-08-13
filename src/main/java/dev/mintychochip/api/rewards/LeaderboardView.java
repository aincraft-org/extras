package dev.mintychochip.api.rewards;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Immutable leaderboard page and optional viewer rank. */
public record LeaderboardView(
    LeaderboardPeriod period,
    String windowKey,
    List<LeaderboardEntry> entries,
    OptionalInt viewerRank) {

  public LeaderboardView {
    Objects.requireNonNull(period, "period");
    windowKey = Objects.requireNonNull(windowKey, "windowKey").trim();
    if (windowKey.isEmpty()) {
      throw new IllegalArgumentException("windowKey must not be blank");
    }
    entries = List.copyOf(entries);
    Objects.requireNonNull(viewerRank, "viewerRank");
    if (viewerRank.isPresent() && viewerRank.getAsInt() <= 0) {
      throw new IllegalArgumentException("viewer rank must be positive");
    }
  }
}
