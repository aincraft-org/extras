package dev.mintychochip.api.rewards;

/** Outcome of recording a login. */
public enum StreakResult {
  STARTED,
  INCREMENTED,
  ALREADY_RECORDED,
  RESET
}
