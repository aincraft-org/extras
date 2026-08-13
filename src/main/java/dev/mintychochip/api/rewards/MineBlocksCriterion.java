package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Criterion for breaking a specific block key. */
public record MineBlocksCriterion(
    String id, String description, MaterialKey block, int target, Reward reward)
    implements Criterion {

  public MineBlocksCriterion {
    id = Criterion.normalizeId(id);
    description = Criterion.normalizeDescription(description);
    Objects.requireNonNull(block, "block");
    target = Criterion.requireTarget(target);
    Objects.requireNonNull(reward, "reward");
  }

  @Override
  public CriterionKind kind() {
    return CriterionKind.MINE_BLOCKS;
  }
}
