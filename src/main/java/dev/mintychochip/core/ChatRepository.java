package dev.mintychochip.core;

import dev.mintychochip.api.ChannelPreferences;
import java.util.Optional;
import java.util.UUID;

/** Persistence surface for chat preferences. */
public interface ChatRepository extends AutoCloseable {

  Optional<ChannelPreferences> load(UUID playerId);

  void save(ChannelPreferences preferences);

  @Override
  void close();
}
