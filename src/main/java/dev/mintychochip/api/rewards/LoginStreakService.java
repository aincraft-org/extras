package dev.mintychochip.api.rewards;

import java.util.UUID;

/** Public API for persistent consecutive UTC login days. */
public interface LoginStreakService extends AutoCloseable {

  StreakSnapshot streak(UUID playerId);

  StreakResult recordLogin(UUID playerId);

  @Override
  void close();
}
