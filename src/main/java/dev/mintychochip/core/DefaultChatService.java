package dev.mintychochip.core;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import dev.mintychochip.api.ChatResult;
import dev.mintychochip.api.ChatService;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default synchronized cached implementation of persistent chat preferences.
 *
 * <p>Each successful preference change persists first, then publishes the matching typed event.
 * Failed and state-preserving operations (already muted, not muted) emit nothing.
 */
public final class DefaultChatService implements ChatService {

  private final ChatRepository repository;
  private final Clock clock;
  private final InProcessExtrasEventService eventService;
  private final ConcurrentMap<UUID, ChannelPreferences> cache = new ConcurrentHashMap<>();
  private final Object mutationLock = new Object();

  public DefaultChatService(ChatRepository repository) {
    this(repository, Clock.systemUTC(), InProcessExtrasEventService.noOp());
  }

  public DefaultChatService(
      ChatRepository repository, Clock clock, InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
  }

  @Override
  public ChannelPreferences preferences(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return cache.computeIfAbsent(
        playerId,
        id ->
            repository
                .load(id)
                .orElseGet(
                    () ->
                        new ChannelPreferences(
                            id, ChannelId.GLOBAL, EnumSet.noneOf(ChannelId.class))));
  }

  @Override
  public ChatResult select(UUID playerId, ChannelId channel) {
    Objects.requireNonNull(channel, "channel");
    synchronized (mutationLock) {
      ChannelPreferences current = preferences(playerId);
      ChannelPreferences updated =
          new ChannelPreferences(playerId, channel, without(current.mutedChannels(), channel));
      persist(updated);
      eventService.publish(
          new ExtrasEvent.ChatChannelSelected(
              UUID.randomUUID(), clock.instant(), playerId, channel.key()));
      return ChatResult.SUCCESS;
    }
  }

  @Override
  public ChatResult mute(UUID playerId, ChannelId channel) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(channel, "channel");
    if (channel == ChannelId.GLOBAL) {
      return ChatResult.GLOBAL_CANNOT_BE_MUTED;
    }
    synchronized (mutationLock) {
      ChannelPreferences current = preferences(playerId);
      if (current.mutedChannels().contains(channel)) {
        return ChatResult.ALREADY_MUTED;
      }
      EnumSet<ChannelId> muted =
          current.mutedChannels().isEmpty()
              ? EnumSet.noneOf(ChannelId.class)
              : EnumSet.copyOf(current.mutedChannels());
      muted.add(channel);
      persist(new ChannelPreferences(playerId, current.activeChannel(), muted));
      eventService.publish(
          new ExtrasEvent.ChatChannelMuted(
              UUID.randomUUID(), clock.instant(), playerId, channel.key()));
      return ChatResult.SUCCESS;
    }
  }

  @Override
  public ChatResult unmute(UUID playerId, ChannelId channel) {
    Objects.requireNonNull(channel, "channel");
    synchronized (mutationLock) {
      ChannelPreferences current = preferences(playerId);
      if (!current.mutedChannels().contains(channel)) {
        return ChatResult.NOT_MUTED;
      }
      persist(
          new ChannelPreferences(
              playerId, current.activeChannel(), without(current.mutedChannels(), channel)));
      eventService.publish(
          new ExtrasEvent.ChatChannelUnmuted(
              UUID.randomUUID(), clock.instant(), playerId, channel.key()));
      return ChatResult.SUCCESS;
    }
  }

  private void persist(ChannelPreferences preferences) {
    repository.save(preferences);
    cache.put(preferences.playerId(), preferences);
  }

  private static EnumSet<ChannelId> without(java.util.Set<ChannelId> channels, ChannelId removed) {
    EnumSet<ChannelId> result =
        channels.isEmpty() ? EnumSet.noneOf(ChannelId.class) : EnumSet.copyOf(channels);
    result.remove(removed);
    return result;
  }
}
