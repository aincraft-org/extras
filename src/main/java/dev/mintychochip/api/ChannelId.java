package dev.mintychochip.api;

import java.util.Locale;
import java.util.Optional;

/** Persistent chat channel identifiers and their stable lowercase keys. */
public enum ChannelId {
  GLOBAL("global"),
  LOCAL("local"),
  PARTY("party"),
  MARKET("market"),
  LFG("lfg");

  private final String key;

  ChannelId(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static Optional<ChannelId> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String normalized = value.toLowerCase(Locale.ROOT);
    for (ChannelId channel : values()) {
      if (channel.key.equals(normalized)) {
        return Optional.of(channel);
      }
    }
    return Optional.empty();
  }
}
