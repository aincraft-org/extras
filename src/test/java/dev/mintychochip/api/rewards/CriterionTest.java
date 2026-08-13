package dev.mintychochip.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CriterionTest {

  @Test
  void acceptsAndNormalizesAValidBlockCriterion() {
    Criterion criterion =
        new MineBlocksCriterion(
            "ore-day",
            " Mine diamond ",
            new MaterialKey("Minecraft", "Diamond_Ore"),
            64,
            Reward.xp(100));
    assertEquals("Mine diamond", criterion.description());
    assertEquals(CriterionKind.MINE_BLOCKS, criterion.kind());
    assertEquals("minecraft:diamond_ore", ((MineBlocksCriterion) criterion).block().toString());
  }

  @Test
  void rejectsBlankIdsInvalidKeysAndNonPositiveTargets() {
    assertThrows(
        IllegalArgumentException.class, () -> new GainXpCriterion("", "xp", 1, Reward.xp(1)));
    assertThrows(IllegalArgumentException.class, () -> new MaterialKey("Minecraft!", "stone"));
    assertThrows(
        IllegalArgumentException.class, () -> new GainXpCriterion("xp", "xp", 0, Reward.xp(1)));
  }

  @Test
  void criterionProgressRejectsBlankKeysAndNonPositiveAmounts() {
    assertThrows(
        IllegalArgumentException.class, () -> new CriterionProgress(CriterionKind.GAIN_XP, "", 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CriterionProgress(CriterionKind.GAIN_XP, "xp", 0));
  }

  @Test
  void criterionMatchesOnlyItsKindAndKey() {
    Criterion criterion =
        new MineBlocksCriterion(
            "ore-day",
            "Mine diamond",
            MaterialKey.parse("minecraft:diamond_ore"),
            64,
            Reward.xp(100));
    assertEquals(
        true,
        criterion.matches(
            new CriterionProgress(CriterionKind.MINE_BLOCKS, "minecraft:diamond_ore", 1)));
    assertEquals(
        false,
        criterion.matches(new CriterionProgress(CriterionKind.MINE_BLOCKS, "minecraft:stone", 1)));
    assertEquals(false, criterion.matches(new CriterionProgress(CriterionKind.GAIN_XP, "xp", 1)));
  }
}
