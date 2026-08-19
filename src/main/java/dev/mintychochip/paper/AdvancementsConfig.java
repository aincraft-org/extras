package dev.mintychochip.paper;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.CustomAdvancement;
import dev.mintychochip.api.rewards.MaterialKey;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Parses operator-defined custom advancements from {@code advancements.yml}. */
public final class AdvancementsConfig {

  private final List<CustomAdvancement> catalog;

  private AdvancementsConfig(List<CustomAdvancement> catalog) {
    this.catalog = List.copyOf(catalog);
  }

  public List<CustomAdvancement> catalog() {
    return catalog;
  }

  public static AdvancementsConfig load(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    File file = new File(plugin.getDataFolder(), "advancements.yml");
    if (!file.exists()) {
      plugin.saveResource("advancements.yml", false);
    }
    return parse(YamlConfiguration.loadConfiguration(file), plugin.getLogger());
  }

  static AdvancementsConfig parse(YamlConfiguration yaml, Logger logger) {
    Objects.requireNonNull(yaml, "yaml");
    Objects.requireNonNull(logger, "logger");
    List<CustomAdvancement> catalog = new ArrayList<>();
    ConfigurationSection root = yaml.getConfigurationSection("advancements");
    if (root == null) {
      return new AdvancementsConfig(catalog);
    }
    for (String id : root.getKeys(false)) {
      ConfigurationSection entry = root.getConfigurationSection(id);
      if (entry == null) {
        logger.warning("Skipping advancement " + id + ": not a section");
        continue;
      }
      try {
        catalog.add(
            new CustomAdvancement(
                id,
                required(entry, "title"),
                required(entry, "description"),
                MaterialKey.parse(required(entry, "icon")),
                AdvancementFrame.parse(entry.getString("frame", "task"))));
      } catch (IllegalArgumentException exception) {
        logger.warning("Skipping advancement " + id + ": " + exception.getMessage());
      }
    }
    return new AdvancementsConfig(catalog);
  }

  private static String required(ConfigurationSection section, String key) {
    String value = section.getString(key);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return value;
  }
}
