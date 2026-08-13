package dev.mintychochip.core;

import dev.mintychochip.api.rewards.LeaderboardEntry;
import dev.mintychochip.api.rewards.LeaderboardPeriod;
import dev.mintychochip.api.rewards.LeaderboardService;
import dev.mintychochip.api.rewards.LeaderboardView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/** SQLite-backed daily and weekly leaderboard projection. */
public final class DefaultLeaderboardService implements LeaderboardService {

  private final SqliteRewardStore store;
  private final DailyWindow window;
  private final Clock clock;

  public DefaultLeaderboardService(SqliteRewardStore store, Clock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.window = new DailyWindow(clock);
  }

  /** Records one matched activity in both the current daily and weekly windows. */
  public void recordProgress(UUID playerId, int amount) {
    Objects.requireNonNull(playerId, "playerId");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    Instant now = clock.instant();
    store.addLeaderboard(playerId, LeaderboardPeriod.DAILY.name(), window.dateKey(), amount, now);
    store.addLeaderboard(playerId, LeaderboardPeriod.WEEKLY.name(), window.weekKey(), amount, now);
  }

  @Override
  public LeaderboardView leaderboard(LeaderboardPeriod period, int limit) {
    Objects.requireNonNull(period, "period");
    if (limit <= 0 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    String windowKey = period == LeaderboardPeriod.DAILY ? window.dateKey() : window.weekKey();
    List<SqliteRewardStore.LeaderboardRow> rows =
        store.leaderboard(period.name(), windowKey, limit);
    List<LeaderboardEntry> entries = new ArrayList<>();
    int rank = 1;
    for (SqliteRewardStore.LeaderboardRow row : rows) {
      entries.add(new LeaderboardEntry(row.playerId(), rank++, row.total()));
    }
    return new LeaderboardView(period, windowKey, entries, OptionalInt.empty());
  }

  public void close() {
    store.close();
  }

  static Comparator<SqliteRewardStore.LeaderboardRow> stableOrder() {
    return Comparator.comparingLong(SqliteRewardStore.LeaderboardRow::total)
        .reversed()
        .thenComparingLong(SqliteRewardStore.LeaderboardRow::updatedAt)
        .thenComparing(SqliteRewardStore.LeaderboardRow::playerId);
  }
}
