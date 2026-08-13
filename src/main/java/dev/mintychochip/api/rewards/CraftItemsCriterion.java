package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Criterion for crafting a specific item key. */
public record CraftItemsCriterion(
    String id, String description, MaterialKey item, int target, Reward reward)
    implements Criterion {

  public CraftItemsCriterion {
    id = Criterion.normalizeId(id);
    description = Criterion.normalizeDescription(description);
    Objects.requireNonNull(item, "item");
    target = Criterion.requireTarget(target);
    Objects.requireNonNull(reward, "reward");
  }

  @Override
  public CriterionKind kind() {
    return CriterionKind.CRAFT_ITEMS;
  }
}
