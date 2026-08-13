package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Criterion for gaining experience points. */
public record GainXpCriterion(String id, String description, int target, Reward reward)
    implements Criterion {

  public GainXpCriterion {
    id = Criterion.normalizeId(id);
    description = Criterion.normalizeDescription(description);
    target = Criterion.requireTarget(target);
    Objects.requireNonNull(reward, "reward");
  }

  @Override
  public CriterionKind kind() {
    return CriterionKind.GAIN_XP;
  }
}
