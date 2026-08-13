package dev.mintychochip.api.rewards;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RewardApiValueTest {

  @Test
  void leaderboardViewOwnsAnImmutableEntryList() {
    List<LeaderboardEntry> entries = new ArrayList<>();
    LeaderboardView view =
        new LeaderboardView(LeaderboardPeriod.DAILY, "2026-08-12", entries, OptionalInt.empty());
    entries.add(new LeaderboardEntry(UUID.randomUUID(), 1, 3));
    assertTrue(view.entries().isEmpty());
    assertThrows(
        UnsupportedOperationException.class,
        () -> view.entries().add(new LeaderboardEntry(UUID.randomUUID(), 1, 1)));
  }

  @Test
  void streakSnapshotRejectsAnImpossibleBestValue() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StreakSnapshot(4, 3, java.util.Optional.empty(), OptionalInt.empty()));
  }
}
