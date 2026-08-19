package dev.mintychochip.api.cinematic;

import java.util.Objects;

/** A camera pose stamped at elapsed seconds from scene start. */
public record CameraKeyframe(double timeSeconds, CameraPose pose) {

  public CameraKeyframe {
    if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
      throw new IllegalArgumentException("timeSeconds must be finite and >= 0");
    }
    Objects.requireNonNull(pose, "pose");
  }
}
