package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.StreakResult;
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

class DefaultLoginStreakServiceTest {

  @TempDir Path tempDir;

  private InProcessExtrasEventService bus;
  private final List<ExtrasEvent> events = new ArrayList<>();

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    bus.subscribe(events::add);
  }

  private DefaultLoginStreakService newService(SqliteRewardStore store, Clock clock) {
    return new DefaultLoginStreakService(store, clock, bus);
  }

  @Test
  void repeatedSameDayLoginIsIdempotent() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    DefaultLoginStreakService service = newService(store, fixed("2026-08-12T12:00:00Z"));
    assertEquals(StreakResult.STARTED, service.recordLogin(player));
    assertEquals(StreakResult.ALREADY_RECORDED, service.recordLogin(player));
    assertEquals(1, service.streak(player).currentStreak());
    service.close();
  }

  @Test
  void consecutiveDayIncrementsAndGapResetsWhileBestPersists() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    MutableClock clock = new MutableClock("2026-08-12T12:00:00Z");
    DefaultLoginStreakService service = newService(store, clock);
    assertEquals(StreakResult.STARTED, service.recordLogin(player));
    clock.set("2026-08-13T12:00:00Z");
    assertEquals(StreakResult.INCREMENTED, service.recordLogin(player));
    clock.set("2026-08-15T12:00:00Z");
    assertEquals(StreakResult.RESET, service.recordLogin(player));
    assertEquals(1, service.streak(player).currentStreak());
    assertEquals(2, service.streak(player).bestStreak());
    service.close();
  }

  // --------------------------------------------------------------- events

  @Test
  void loginChangesEmitStreakEvents() {
    UUID player = UUID.randomUUID();
    SqliteRewardStore store = new SqliteRewardStore(tempDir.resolve("rewards.db"));
    MutableClock clock = new MutableClock("2026-08-12T12:00:00Z");
    DefaultLoginStreakService service = newService(store, clock);

    assertEquals(StreakResult.STARTED, service.recordLogin(player));
    assertSingleEvent(
        ExtrasEvent.LoginStreakChanged.class,
        event -> {
          assertEquals(player, event.playerId());
          assertEquals(1, event.streak());
        });

    clock.set("2026-08-13T12:00:00Z");
    assertEquals(StreakResult.INCREMENTED, service.recordLogin(player));
    assertSingleEvent(
        ExtrasEvent.LoginStreakChanged.class, event -> assertEquals(2, event.streak()));

    clock.set("2026-08-15T12:00:00Z");
    assertEquals(StreakResult.RESET, service.recordLogin(player));
    assertSingleEvent(
        ExtrasEvent.LoginStreakChanged.class, event -> assertEquals(1, event.streak()));

    // Same-day repeat is a no-op.
    assertEquals(StreakResult.ALREADY_RECORDED, service.recordLogin(player));
    assertTrue(events.isEmpty());
    service.close();
  }

  private <E extends ExtrasEvent> void assertSingleEvent(
      Class<E> type, java.util.function.Consumer<E> assertions) {
    assertEquals(1, events.size(), "expected exactly one event, got: " + events);
    ExtrasEvent event = events.get(0);
    assertTrue(type.isInstance(event), "expected " + type.getSimpleName() + " but got " + event);
    assertions.accept(type.cast(event));
    events.clear();
  }

  private static Clock fixed(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
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
