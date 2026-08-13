package dev.mintychochip.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable validated plain-text chat message. */
public record ChatMessage(UUID senderId, ChannelId channel, String text, Instant createdAt) {
  public ChatMessage {
    Objects.requireNonNull(senderId, "senderId");
    Objects.requireNonNull(channel, "channel");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(createdAt, "createdAt");
    text = text.trim();
    int codePoints = text.codePointCount(0, text.length());
    if (codePoints == 0 || codePoints > 256) {
      throw new IllegalArgumentException("message must contain 1 to 256 Unicode code points");
    }
  }
}
