package dev.mintychochip.paper;

import dev.mintychochip.api.ChannelId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Pure channel cooldown policy and decision helpers. */
public final class ChatCooldowns {
  private static final ConcurrentMap<Key, Instant> LAST = new ConcurrentHashMap<>();

  private ChatCooldowns() {}

  public enum Decision {
    ALLOW,
    REJECT
  }

  public static Duration admit(UUID playerId, ChannelId channel, Instant now) {
    Key key = new Key(playerId, channel);
    Duration[] remaining = {Duration.ZERO};
    LAST.compute(
        key,
        (ignored, previous) -> {
          remaining[0] = remaining(channel, previous, now);
          return remaining[0].isZero() ? now : previous;
        });
    return remaining[0];
  }

  public static void clear(UUID playerId) {
    LAST.keySet().removeIf(key -> key.playerId().equals(playerId));
  }

  public static Duration duration(ChannelId channel) {
    Objects.requireNonNull(channel, "channel");
    return switch (channel) {
      case GLOBAL -> Duration.ofSeconds(2);
      case LOCAL, PARTY -> Duration.ofSeconds(1);
      case MARKET, LFG -> Duration.ofSeconds(5);
    };
  }

  public static Decision decide(ChannelId channel, Instant previous, Instant now) {
    return remaining(channel, previous, now).isZero() ? Decision.ALLOW : Decision.REJECT;
  }

  public static Duration remaining(ChannelId channel, Instant previous, Instant now) {
    Objects.requireNonNull(now, "now");
    if (previous == null) {
      return Duration.ZERO;
    }
    Duration elapsed = Duration.between(previous, now);
    Duration remaining = duration(channel).minus(elapsed);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  private record Key(UUID playerId, ChannelId channel) {}
}
