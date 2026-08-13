package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.api.ChannelId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatCooldownDecisionTest {
  @Test
  void cooldownsMatchChannelPolicy() {
    assertEquals(Duration.ofSeconds(2), ChatCooldowns.duration(ChannelId.GLOBAL));
    assertEquals(Duration.ofSeconds(1), ChatCooldowns.duration(ChannelId.LOCAL));
    assertEquals(Duration.ofSeconds(1), ChatCooldowns.duration(ChannelId.PARTY));
    assertEquals(Duration.ofSeconds(5), ChatCooldowns.duration(ChannelId.MARKET));
    assertEquals(Duration.ofSeconds(5), ChatCooldowns.duration(ChannelId.LFG));
  }

  @Test
  void decisionIsPureAndReportsRemainingTime() {
    Instant now = Instant.parse("2026-08-12T00:00:03Z");
    Instant previous = Instant.parse("2026-08-12T00:00:02Z");
    assertEquals(ChatCooldowns.Decision.ALLOW, ChatCooldowns.decide(ChannelId.GLOBAL, null, now));
    assertEquals(
        ChatCooldowns.Decision.REJECT, ChatCooldowns.decide(ChannelId.GLOBAL, previous, now));
    assertEquals(Duration.ofSeconds(1), ChatCooldowns.remaining(ChannelId.GLOBAL, previous, now));
  }

  @Test
  void atomicAdmissionReportsRemainingDelay() {
    java.util.UUID playerId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000123");
    Instant first = Instant.parse("2026-08-12T00:00:00Z");
    ChatCooldowns.clear(playerId);

    assertEquals(Duration.ZERO, ChatCooldowns.admit(playerId, ChannelId.MARKET, first));
    assertEquals(
        Duration.ofMillis(4500),
        ChatCooldowns.admit(playerId, ChannelId.MARKET, first.plusMillis(500)));
  }
}
