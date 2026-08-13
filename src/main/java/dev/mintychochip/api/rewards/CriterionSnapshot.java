package dev.mintychochip.api.rewards;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable active-criterion view including its UTC day. */
public record CriterionSnapshot(LocalDate day, Criterion criterion) {

  public CriterionSnapshot {
    Objects.requireNonNull(day, "day");
    Objects.requireNonNull(criterion, "criterion");
  }
}
