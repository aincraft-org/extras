package dev.mintychochip.api;

/** Outcome of a title grant / revoke / equip attempt. */
public enum TitleResult {
  /** The title was granted, revoked, equipped, or unequipped. */
  SUCCESS,
  /** Grant attempted on a title id already in the player's unlocked set. */
  ALREADY_UNLOCKED,
  /** Equip/revoke attempted on a title id the player does not possess. */
  NOT_UNLOCKED,
  /** The title id failed validation (blank, oversized, or control chars). */
  INVALID_TITLE
}
