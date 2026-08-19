package dev.mintychochip.api.cinematic;

import java.util.Locale;
import java.util.Objects;

/**
 * Timed shader overlay window. Active when {@code startSeconds <= t <= endSeconds}.
 *
 * <p>{@code overlayId} is a vanilla post/core effect id such as {@code darkness} or {@code nausea}.
 */
public record OverlayCue(String overlayId, double startSeconds, double endSeconds) {

  public OverlayCue {
    Objects.requireNonNull(overlayId, "overlayId");
    String trimmed = overlayId.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("overlayId must not be blank");
    }
    overlayId = trimmed;
    requireWindow(startSeconds, endSeconds);
  }

  /** Whether elapsed {@code t} lies inside this inclusive time window. */
  public boolean contains(double t) {
    return t >= startSeconds && t <= endSeconds;
  }

  static void requireWindow(double startSeconds, double endSeconds) {
    if (!Double.isFinite(startSeconds) || !Double.isFinite(endSeconds)) {
      throw new IllegalArgumentException("cue times must be finite");
    }
    if (startSeconds < 0.0) {
      throw new IllegalArgumentException("cue start must be >= 0");
    }
    if (endSeconds < startSeconds) {
      throw new IllegalArgumentException("cue end must be >= start");
    }
  }
}
