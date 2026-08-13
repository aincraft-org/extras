package dev.mintychochip.core;

import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.LoginStreakService;
import dev.mintychochip.api.rewards.StreakResult;
import dev.mintychochip.api.rewards.StreakSnapshot;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Persistent UTC login streak implementation. */
public final class DefaultLoginStreakService implements LoginStreakService {

  private final SqliteRewardStore store;
  private final DailyWindow window;
  private final InProcessExtrasEventService eventService;
  private final Object mutationLock = new Object();

  public DefaultLoginStreakService(SqliteRewardStore store, Clock clock) {
    this(store, clock, InProcessExtrasEventService.noOp());
  }

  public DefaultLoginStreakService(
      SqliteRewardStore store, Clock clock, InProcessExtrasEventService eventService) {
    this.store = Objects.requireNonNull(store, "store");
    this.window = new DailyWindow(clock);
    this.eventService = Objects.requireNonNull(eventService, "eventService");
  }

  @Override
  public StreakSnapshot streak(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return store
        .findStreak(playerId)
        .map(row -> snapshot(row.current(), row.best(), row.lastLogin()))
        .orElseGet(() -> emptySnapshot());
  }

  @Override
  public StreakResult recordLogin(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    LocalDate today = window.date();
    synchronized (mutationLock) {
      Optional<SqliteRewardStore.StreakRow> existing = store.findStreak(playerId);
      if (existing.isEmpty()) {
        store.saveStreak(playerId, 1, 1, today);
        publishChanged(playerId, 1);
        return StreakResult.STARTED;
      }
      SqliteRewardStore.StreakRow row = existing.get();
      if (row.lastLogin().equals(today)) {
        return StreakResult.ALREADY_RECORDED;
      }
      int current = row.lastLogin().plusDays(1).equals(today) ? row.current() + 1 : 1;
      int best = Math.max(row.best(), current);
      store.saveStreak(playerId, current, best, today);
      publishChanged(playerId, current);
      return current == 1 ? StreakResult.RESET : StreakResult.INCREMENTED;
    }
  }

  /** Publishes {@link ExtrasEvent.LoginStreakChanged} after a successful streak change. */
  private void publishChanged(UUID playerId, int streak) {
    eventService.publish(
        new ExtrasEvent.LoginStreakChanged(
            UUID.randomUUID(), window.clock().instant(), playerId, streak));
  }

  @Override
  public void close() {
    store.close();
  }

  private static StreakSnapshot emptySnapshot() {
    return new StreakSnapshot(0, 0, Optional.empty(), OptionalInt.empty());
  }

  private static StreakSnapshot snapshot(int current, int best, LocalDate lastLogin) {
    return new StreakSnapshot(current, best, Optional.of(lastLogin), OptionalInt.empty());
  }
}
