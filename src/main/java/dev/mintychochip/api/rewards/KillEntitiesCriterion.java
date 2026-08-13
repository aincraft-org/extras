package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Criterion for killing a specific entity key. */
public record KillEntitiesCriterion(
    String id, String description, MaterialKey entity, int target, Reward reward)
    implements Criterion {

  public KillEntitiesCriterion {
    id = Criterion.normalizeId(id);
    description = Criterion.normalizeDescription(description);
    Objects.requireNonNull(entity, "entity");
    target = Criterion.requireTarget(target);
    Objects.requireNonNull(reward, "reward");
  }

  @Override
  public CriterionKind kind() {
    return CriterionKind.KILL_ENTITIES;
  }
}
