package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.api.rewards.LeaderboardPeriod;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultLeaderboardServiceTest {

  @TempDir Path tempDir;

  @Test
  void ranksDescendingAndSeparatesPeriods() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    DefaultLeaderboardService service = new DefaultLeaderboardService(store, clock());
    service.recordProgress(first, 5);
    service.recordProgress(second, 9);
    assertEquals(
        second, service.leaderboard(LeaderboardPeriod.DAILY, 10).entries().get(0).playerId());
    assertEquals(2, service.leaderboard(LeaderboardPeriod.WEEKLY, 10).entries().size());
    service.close();
  }

  @Test
  void rejectsInvalidLimits() {
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    DefaultLeaderboardService service = new DefaultLeaderboardService(store, clock());
    assertThrows(
        IllegalArgumentException.class, () -> service.leaderboard(LeaderboardPeriod.DAILY, 0));
    service.close();
  }

  private static Clock clock() {
    return Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC);
  }
}
