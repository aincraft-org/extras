package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Immutable activity increment submitted by a platform adapter. */
public record CriterionProgress(CriterionKind kind, String key, int amount) {

  public CriterionProgress {
    Objects.requireNonNull(kind, "kind");
    key = Objects.requireNonNull(key, "key").trim().toLowerCase(java.util.Locale.ROOT);
    if (key.isEmpty()) {
      throw new IllegalArgumentException("key must not be blank");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
  }
}
