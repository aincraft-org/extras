package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.mintychochip.api.rewards.CriterionKind;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RewardsConfigTest {

  @Test
  void parsesCriterionPoolAndDefaults() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("daily-reward.type", "XP");
    yaml.set("daily-reward.payload", "xp");
    yaml.set("daily-reward.amount", 100);
    java.util.Map<String, Object> criterion = new java.util.HashMap<>();
    criterion.put("id", "ore-day");
    criterion.put("type", "MINE_BLOCKS");
    criterion.put("block", "minecraft:diamond_ore");
    criterion.put("target", 16);
    criterion.put("description", "Mine diamonds");
    yaml.set("criterion-pool", java.util.List.of(criterion));
    RewardsConfig config = RewardsConfig.parse(yaml, Logger.getLogger("test"));
    assertEquals(CriterionKind.MINE_BLOCKS, config.criterionPool().get(0).kind());
    assertEquals("OPTIONAL", config.providerMode());
  }

  @Test
  void emptyPoolUsesSafeLoginFallback() {
    RewardsConfig config = RewardsConfig.parse(new YamlConfiguration(), Logger.getLogger("test"));
    assertFalse(config.criterionPool().isEmpty());
    assertEquals(CriterionKind.LOGIN_DAYS, config.criterionPool().get(0).kind());
  }
}
