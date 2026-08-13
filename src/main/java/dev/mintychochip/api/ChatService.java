package dev.mintychochip.api;

import java.util.UUID;

/** Bukkit-free persistent chat preference service. */
public interface ChatService {

  ChannelPreferences preferences(UUID playerId);

  ChatResult select(UUID playerId, ChannelId channel);

  ChatResult mute(UUID playerId, ChannelId channel);

  ChatResult unmute(UUID playerId, ChannelId channel);
}
