package dev.mintychochip.api;

/** Outcome of a chat preference operation. */
public enum ChatResult {
  SUCCESS,
  GLOBAL_CANNOT_BE_MUTED,
  ALREADY_MUTED,
  NOT_MUTED
}
