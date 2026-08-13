package dev.mintychochip.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelIdTest {

  @Test
  void parsesStableLowercaseKeys() {
    assertEquals(Optional.of(ChannelId.GLOBAL), ChannelId.parse("GLOBAL"));
    assertEquals(Optional.of(ChannelId.LOCAL), ChannelId.parse("local"));
    assertEquals(Optional.of(ChannelId.PARTY), ChannelId.parse("PaRtY"));
    assertEquals(Optional.of(ChannelId.MARKET), ChannelId.parse("market"));
    assertEquals(Optional.of(ChannelId.LFG), ChannelId.parse("LFG"));
    assertEquals("global", ChannelId.GLOBAL.key());
  }

  @Test
  void rejectsUnknownAndBlankKeys() {
    assertTrue(ChannelId.parse(null).isEmpty());
    assertTrue(ChannelId.parse("").isEmpty());
    assertTrue(ChannelId.parse(" trade ").isEmpty());
  }

  @Test
  void preferencesCannotContainGlobalMute() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ChannelPreferences(UUID.randomUUID(), ChannelId.LOCAL, Set.of(ChannelId.GLOBAL)));
  }
}
