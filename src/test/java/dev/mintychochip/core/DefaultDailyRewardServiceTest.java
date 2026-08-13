package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.Criterion;
import dev.mintychochip.api.rewards.CriterionKind;
import dev.mintychochip.api.rewards.CriterionProgress;
import dev.mintychochip.api.rewards.DailyRewardResult;
import dev.mintychochip.api.rewards.GainXpCriterion;
import dev.mintychochip.api.rewards.Reward;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultDailyRewardServiceTest {

  @TempDir Path tempDir;

  private InProcessExtrasEventService bus;
  private final List<ExtrasEvent> events = new ArrayList<>();

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    bus.subscribe(events::add);
  }

  private DefaultDailyRewardService newService(SqliteRewardStore store, Clock clock) {
    return new DefaultDailyRewardService(
        store, clock, new GainXpCriterion("xp-day", "Earn XP", 10, Reward.xp(100)), bus);
  }

  @Test
  void matchingProgressClampsAtTargetAndClaimIsIdempotent() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    DefaultDailyRewardService service =
        new DefaultDailyRewardService(
            store,
            Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC),
            new GainXpCriterion("xp-day", "Earn XP", 10, Reward.xp(100)));
    assertEquals(
        DailyRewardResult.COMPLETED,
        service.recordProgress(player, new CriterionProgress(CriterionKind.GAIN_XP, "xp", 20)));
    assertEquals(10, service.status(player).progress());
    assertEquals(Reward.xp(100), service.claim(player).reward());
    assertEquals(DailyRewardResult.ALREADY_CLAIMED, service.claim(player).result());
    service.close();
  }

  @Test
  void mismatchedKeyDoesNotChangeProgress() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    DefaultDailyRewardService service =
        new DefaultDailyRewardService(
            store,
            Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC),
            new GainXpCriterion("xp-day", "Earn XP", 10, Reward.xp(100)));
    assertEquals(
        DailyRewardResult.IGNORED,
        service.recordProgress(player, new CriterionProgress(CriterionKind.GAIN_XP, "other", 5)));
    assertEquals(0, service.status(player).progress());
    service.close();
  }

  @Test
  void selectsAStableDifferentPoolCriterionOnTheNextUtcDay() {
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    MutableClock clock = new MutableClock("2026-08-12T12:00:00Z");
    Criterion first = new GainXpCriterion("first", "First", 1, Reward.xp(1));
    Criterion second = new GainXpCriterion("second", "Second", 1, Reward.xp(1));
    DefaultDailyRewardService service =
        new DefaultDailyRewardService(store, clock, first, List.of(first, second));
    String firstId = service.activeCriterion().criterion().id();
    clock.set("2026-08-13T12:00:00Z");
    String secondId = service.activeCriterion().criterion().id();
    assertNotEquals(firstId, secondId);
    assertEquals(true, List.of("first", "second").contains(firstId));
    assertEquals(true, List.of("first", "second").contains(secondId));
    service.close();
  }

  // --------------------------------------------------------------- events

  @Test
  void claimEmitsRewardClaimedEvent() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    Clock clock = Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC);
    DefaultDailyRewardService service = newService(store, clock);

    service.recordProgress(player, new CriterionProgress(CriterionKind.GAIN_XP, "xp", 20));
    events.clear();
    assertEquals(Reward.xp(100), service.claim(player).reward());
    assertSingleEvent(
        ExtrasEvent.RewardClaimed.class,
        event -> {
          assertEquals(player, event.playerId());
          assertEquals("xp", event.rewardType());
          assertEquals("2026-08-12", event.day());
        });

    // Duplicate claim changes nothing and emits nothing.
    assertEquals(DailyRewardResult.ALREADY_CLAIMED, service.claim(player).result());
    assertTrue(events.isEmpty());
    service.close();
  }

  @Test
  void progressAndUnclaimableClaimEmitNoEvents() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    DefaultDailyRewardService service = newService(store, fixed("2026-08-12T12:00:00Z"));

    assertEquals(
        DailyRewardResult.PROGRESSED,
        service.recordProgress(player, new CriterionProgress(CriterionKind.GAIN_XP, "xp", 3)));
    assertEquals(DailyRewardResult.NOT_CLAIMABLE, service.claim(player).result());
    assertEquals(
        DailyRewardResult.IGNORED,
        service.recordProgress(player, new CriterionProgress(CriterionKind.GAIN_XP, "other", 3)));
    assertTrue(events.isEmpty());
    service.close();
  }

  private static Clock fixed(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  private <E extends ExtrasEvent> void assertSingleEvent(
      Class<E> type, java.util.function.Consumer<E> assertions) {
    assertEquals(1, events.size(), "expected exactly one event, got: " + events);
    ExtrasEvent event = events.get(0);
    assertTrue(type.isInstance(event), "expected " + type.getSimpleName() + " but got " + event);
    assertions.accept(type.cast(event));
    events.clear();
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(String value) {
      instant = Instant.parse(value);
    }

    private void set(String value) {
      instant = Instant.parse(value);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
