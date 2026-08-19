package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.CustomAdvancement;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AdvancementsConfigTest {

  @Test
  void parsesCatalogDisplayFieldsAndFrames() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("advancements.first-login.title", "Welcome");
    yaml.set("advancements.first-login.description", "Join the server");
    yaml.set("advancements.first-login.icon", "minecraft:oak_door");
    yaml.set("advancements.first-login.frame", "task");
    yaml.set("advancements.party-maker.title", "Let's Party");
    yaml.set("advancements.party-maker.description", "Create a party");
    yaml.set("advancements.party-maker.icon", "minecraft:cake");
    yaml.set("advancements.party-maker.frame", "goal");
    yaml.set("advancements.extras-legend.title", "Extras Legend");
    yaml.set("advancements.extras-legend.description", "Legendary");
    yaml.set("advancements.extras-legend.icon", "minecraft:nether_star");
    yaml.set("advancements.extras-legend.frame", "challenge");

    AdvancementsConfig config = AdvancementsConfig.parse(yaml, Logger.getLogger("test"));
    assertEquals(3, config.catalog().size());
    CustomAdvancement first = config.catalog().get(0);
    assertEquals("first-login", first.id());
    assertEquals("Welcome", first.title());
    assertEquals("minecraft:oak_door", first.icon().toString());
    assertEquals(AdvancementFrame.TASK, first.frame());
    assertEquals(AdvancementFrame.GOAL, config.catalog().get(1).frame());
    assertEquals(AdvancementFrame.CHALLENGE, config.catalog().get(2).frame());
  }

  @Test
  void emptyFileYieldsEmptyCatalog() {
    AdvancementsConfig config =
        AdvancementsConfig.parse(new YamlConfiguration(), Logger.getLogger("test"));
    assertTrue(config.catalog().isEmpty());
  }
}
