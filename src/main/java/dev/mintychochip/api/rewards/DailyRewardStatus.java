package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Immutable per-player view of today's criterion progress and claim state. */
public record DailyRewardStatus(
    CriterionSnapshot criterion, int progress, boolean completed, boolean claimed) {

  public DailyRewardStatus {
    Objects.requireNonNull(criterion, "criterion");
    if (progress < 0) {
      throw new IllegalArgumentException("progress must not be negative");
    }
  }
}
