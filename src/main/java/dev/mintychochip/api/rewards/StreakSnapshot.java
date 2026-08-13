package dev.mintychochip.api.rewards;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable login-streak state. */
public record StreakSnapshot(
    int currentStreak, int bestStreak, Optional<LocalDate> lastLogin, OptionalInt nextMilestone) {

  public StreakSnapshot {
    if (currentStreak < 0 || bestStreak < 0 || bestStreak < currentStreak) {
      throw new IllegalArgumentException("invalid streak values");
    }
    Objects.requireNonNull(lastLogin, "lastLogin");
    Objects.requireNonNull(nextMilestone, "nextMilestone");
    if (nextMilestone.isPresent() && nextMilestone.getAsInt() <= 0) {
      throw new IllegalArgumentException("next milestone must be positive");
    }
  }
}
