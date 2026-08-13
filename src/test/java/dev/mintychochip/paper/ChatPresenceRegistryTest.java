package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.core.PresenceSnapshot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatPresenceRegistryTest {
  @Test
  void snapshotsAreImmutableAndReplaceByUuid() {
    ChatPresenceRegistry registry = new ChatPresenceRegistry();
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    PresenceSnapshot first = new PresenceSnapshot(playerId, worldId, 1, 2, 3);
    PresenceSnapshot second = new PresenceSnapshot(playerId, worldId, 4, 5, 6);

    registry.update(first);
    assertEquals(first, registry.snapshot(playerId).orElseThrow());
    assertThrows(UnsupportedOperationException.class, () -> registry.snapshots().clear());

    registry.update(second);
    assertEquals(second, registry.snapshot(playerId).orElseThrow());
    registry.remove(playerId);
    assertEquals(java.util.Optional.empty(), registry.snapshot(playerId));
  }

  @Test
  void snapshotsDoNotExposeMutableBackingMap() {
    ChatPresenceRegistry registry = new ChatPresenceRegistry();
    UUID playerId = UUID.randomUUID();
    registry.update(new PresenceSnapshot(playerId, UUID.randomUUID(), 0, 0, 0));
    var view = registry.snapshots();
    assertThrows(UnsupportedOperationException.class, () -> view.remove(playerId));
    assertEquals(1, registry.snapshots().size());
  }
}
