package dev.mintychochip.paper;

import dev.mintychochip.core.PresenceSnapshot;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Concurrent registry of immutable player presence snapshots captured on safe entity contexts. */
public final class ChatPresenceRegistry {
  private final ConcurrentMap<UUID, PresenceSnapshot> snapshots = new ConcurrentHashMap<>();

  public void update(PresenceSnapshot snapshot) {
    snapshots.put(snapshot.playerId(), snapshot);
  }

  public void remove(UUID playerId) {
    snapshots.remove(playerId);
  }

  public Optional<PresenceSnapshot> snapshot(UUID playerId) {
    return Optional.ofNullable(snapshots.get(playerId));
  }

  /** Returns an immutable point-in-time map view suitable for async routing. */
  public Map<UUID, PresenceSnapshot> snapshots() {
    return Map.copyOf(snapshots);
  }
}
