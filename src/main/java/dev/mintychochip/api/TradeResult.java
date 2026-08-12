package dev.mintychochip.api;

/** Outcome of a player trade operation. */
public enum TradeResult {
  SUCCESS,
  SELF_TRADE,
  REQUEST_EXISTS,
  NO_REQUEST,
  ALREADY_TRADING,
  NOT_PARTICIPANT,
  STALE_OFFER,
  NOT_CONFIRMED,
  ALREADY_CONFIRMED,
  ALREADY_COMPLETED
}
