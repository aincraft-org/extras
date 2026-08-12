package dev.mintychochip.api;

/** Outcome of a friend operation. */
public enum FriendResult {
  SUCCESS,
  SELF_REQUEST,
  ALREADY_FRIENDS,
  REQUEST_EXISTS,
  NO_REQUEST,
  NOT_FRIENDS
}
