package dev.mintychochip.api;

/** Outcome of granting a custom advancement. */
public enum AdvancementResult {
  /** First-time grant: completion was persisted. */
  GRANTED,
  /** The player already had this advancement; no second completion was written. */
  ALREADY_COMPLETED,
  /** The id is not in the catalog; nothing was persisted. */
  UNKNOWN_ADVANCEMENT
}
