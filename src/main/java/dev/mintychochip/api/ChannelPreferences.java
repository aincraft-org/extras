package dev.mintychochip.api;

import java.util.Set;
import java.util.UUID;

/** Immutable snapshot of one player's chat preferences. */
public record ChannelPreferences(
    UUID playerId, ChannelId activeChannel, Set<ChannelId> mutedChannels) {

  public ChannelPreferences {
    if (playerId == null || activeChannel == null || mutedChannels == null) {
      throw new NullPointerException("preference fields must not be null");
    }
    if (mutedChannels.contains(ChannelId.GLOBAL)) {
      throw new IllegalArgumentException("GLOBAL cannot be muted");
    }
    mutedChannels = Set.copyOf(mutedChannels);
  }
}
