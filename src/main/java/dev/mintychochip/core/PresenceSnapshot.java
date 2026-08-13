package dev.mintychochip.core;

import java.util.Objects;
import java.util.UUID;

/** Bukkit-free immutable player location snapshot used for routing. */
public record PresenceSnapshot(UUID playerId, UUID worldId, double x, double y, double z) {
  public PresenceSnapshot {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(worldId, "worldId");
  }
}
