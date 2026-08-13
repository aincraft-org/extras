package dev.mintychochip.api;

import java.util.Set;
import java.util.UUID;

/** Immutable result of routing an accepted chat message. */
public record ChatDelivery(boolean accepted, Set<UUID> recipients) {
  public ChatDelivery {
    recipients = Set.copyOf(recipients);
  }

  public static ChatDelivery accepted(Set<UUID> recipients) {
    return new ChatDelivery(true, recipients);
  }

  public static ChatDelivery rejected() {
    return new ChatDelivery(false, Set.of());
  }
}
