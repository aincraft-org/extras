package dev.mintychochip.core;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Objects;

/** UTC date and ISO-week keys used by rewards and leaderboards. */
final class DailyWindow {

  private final Clock clock;

  DailyWindow(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** The injected clock; used for event timestamps. */
  Clock clock() {
    return clock;
  }

  LocalDate date() {
    return date(clock.instant());
  }

  LocalDate date(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate();
  }

  String dateKey() {
    return date().toString();
  }

  String weekKey() {
    LocalDate date = date();
    WeekFields fields = WeekFields.ISO;
    return "%d-W%02d"
        .formatted(date.get(fields.weekBasedYear()), date.get(fields.weekOfWeekBasedYear()));
  }

  static LocalDate date(Clock clock) {
    return new DailyWindow(clock).date();
  }
}
