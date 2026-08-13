package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Bukkit-free definition of a progress criterion for a daily reward. */
public sealed interface Criterion
    permits MineBlocksCriterion,
        KillEntitiesCriterion,
        CraftItemsCriterion,
        GainXpCriterion,
        LoginDaysCriterion,
        PlayTimeCriterion {

  String id();

  String description();

  CriterionKind kind();

  int target();

  Reward reward();

  default boolean matches(CriterionProgress progress) {
    Objects.requireNonNull(progress, "progress");
    if (progress.kind() != kind()) {
      return false;
    }
    return switch (this) {
      case MineBlocksCriterion criterion -> criterion.block().toString().equals(progress.key());
      case KillEntitiesCriterion criterion -> criterion.entity().toString().equals(progress.key());
      case CraftItemsCriterion criterion -> criterion.item().toString().equals(progress.key());
      case GainXpCriterion ignored -> "xp".equals(progress.key());
      case LoginDaysCriterion ignored -> "login".equals(progress.key());
      case PlayTimeCriterion ignored -> "seconds".equals(progress.key());
    };
  }

  static String normalizeId(String value) {
    return normalizeText(value, "id");
  }

  static String normalizeDescription(String value) {
    return normalizeText(value, "description");
  }

  static int requireTarget(int value) {
    if (value <= 0) {
      throw new IllegalArgumentException("target must be positive");
    }
    return value;
  }

  static String normalizeText(String value, String field) {
    Objects.requireNonNull(value, field);
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }
}
