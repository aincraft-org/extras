package dev.mintychochip.api.cinematic;

import java.util.Locale;
import java.util.Objects;

/**
 * Timed prop window with a spawn pose. Active when {@code startSeconds <= t <= endSeconds}.
 *
 * <p>{@code propId} is a material-like key (for example {@code oak_sign} or {@code lantern}) that
 * Paper maps onto a display entity.
 */
public record PropCue(String propId, double startSeconds, double endSeconds, CameraPose pose) {

  public PropCue {
    Objects.requireNonNull(propId, "propId");
    String trimmed = propId.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("propId must not be blank");
    }
    propId = trimmed;
    OverlayCue.requireWindow(startSeconds, endSeconds);
    Objects.requireNonNull(pose, "pose");
  }

  /** Whether elapsed {@code t} lies inside this inclusive time window. */
  public boolean contains(double t) {
    return t >= startSeconds && t <= endSeconds;
  }
}
