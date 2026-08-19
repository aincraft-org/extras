package dev.mintychochip.api.cinematic;

/** Outcome of a cinematic scene mutation or play attempt. */
public enum CinematicResult {
  SUCCESS,
  TOO_FEW_KEYFRAMES,
  UNKNOWN_SCENE,
  INVALID_NAME,
  ALREADY_EXISTS,
  ALREADY_PLAYING,
  INVALID_KEYFRAME,
  INVALID_CUE
}
