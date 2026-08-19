package dev.mintychochip.api.cinematic;

import java.util.Objects;

/**
 * Bukkit-free camera pose: world identity plus position and look angles.
 *
 * <p>{@code worldIdentity} is a world UUID string or world name; Paper maps it onto a {@code
 * World}. Yaw and pitch use Minecraft's degrees (yaw: 0 = south, pitch: 0 = horizon).
 */
public record CameraPose(
    String worldIdentity, double x, double y, double z, float yaw, float pitch) {

  public CameraPose {
    Objects.requireNonNull(worldIdentity, "worldIdentity");
    String trimmed = worldIdentity.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("worldIdentity must not be blank");
    }
    worldIdentity = trimmed;
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
      throw new IllegalArgumentException("position must be finite");
    }
    if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
      throw new IllegalArgumentException("yaw and pitch must be finite");
    }
  }
}
