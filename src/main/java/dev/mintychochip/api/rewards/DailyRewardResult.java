package dev.mintychochip.api.rewards;

/** Outcome of a daily-reward mutation. */
public enum DailyRewardResult {
  PROGRESSED,
  COMPLETED,
  CLAIMED,
  ALREADY_CLAIMED,
  NOT_CLAIMABLE,
  IGNORED,
  INVALID_CRITERION,
  ROTATED
}
