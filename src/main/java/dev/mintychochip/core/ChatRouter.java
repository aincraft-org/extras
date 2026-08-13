package dev.mintychochip.core;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import dev.mintychochip.api.ChatDelivery;
import dev.mintychochip.api.ChatMessage;
import dev.mintychochip.api.Party;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Pure Bukkit-free chat recipient selection. */
public final class ChatRouter {
  private static final double LOCAL_RADIUS_SQUARED = 10_000.0;

  public ChatDelivery route(
      ChatMessage message,
      PresenceSnapshot sender,
      Collection<PresenceSnapshot> presences,
      Optional<Party> party,
      Function<UUID, ChannelPreferences> preferences) {
    if (message.channel() == ChannelId.PARTY
        && (party.isEmpty() || !party.get().hasMember(message.senderId()))) {
      return ChatDelivery.rejected();
    }
    Set<UUID> recipients = new HashSet<>();
    for (PresenceSnapshot presence : presences) {
      if (!eligible(message.channel(), sender, presence, party)) {
        continue;
      }
      ChannelPreferences recipientPreferences = preferences.apply(presence.playerId());
      if (!presence.playerId().equals(message.senderId())
          && recipientPreferences != null
          && recipientPreferences.mutedChannels().contains(message.channel())) {
        continue;
      }
      recipients.add(presence.playerId());
    }
    return ChatDelivery.accepted(recipients);
  }

  private static boolean eligible(
      ChannelId channel,
      PresenceSnapshot sender,
      PresenceSnapshot recipient,
      Optional<Party> party) {
    return switch (channel) {
      case GLOBAL, MARKET, LFG -> true;
      case LOCAL ->
          sender.worldId().equals(recipient.worldId())
              && squaredDistance(sender, recipient) <= LOCAL_RADIUS_SQUARED;
      case PARTY -> party.isPresent() && party.get().hasMember(recipient.playerId());
    };
  }

  private static double squaredDistance(PresenceSnapshot first, PresenceSnapshot second) {
    double dx = first.x() - second.x();
    double dy = first.y() - second.y();
    double dz = first.z() - second.z();
    return dx * dx + dy * dy + dz * dz;
  }
}
