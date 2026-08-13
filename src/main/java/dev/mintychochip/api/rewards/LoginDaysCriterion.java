package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Criterion for recording distinct login days. */
public record LoginDaysCriterion(String id, String description, int target, Reward reward)
    implements Criterion {

  public LoginDaysCriterion {
    id = Criterion.normalizeId(id);
    description = Criterion.normalizeDescription(description);
    target = Criterion.requireTarget(target);
    Objects.requireNonNull(reward, "reward");
  }

  @Override
  public CriterionKind kind() {
    return CriterionKind.LOGIN_DAYS;
  }
}
