package dev.mintychochip.paper;

import dev.mintychochip.api.rewards.CraftItemsCriterion;
import dev.mintychochip.api.rewards.Criterion;
import dev.mintychochip.api.rewards.GainXpCriterion;
import dev.mintychochip.api.rewards.KillEntitiesCriterion;
import dev.mintychochip.api.rewards.LoginDaysCriterion;
import dev.mintychochip.api.rewards.MaterialKey;
import dev.mintychochip.api.rewards.MineBlocksCriterion;
import dev.mintychochip.api.rewards.PlayTimeCriterion;
import dev.mintychochip.api.rewards.Reward;
import dev.mintychochip.api.rewards.RewardType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Parses and owns the Paper-side rewards.yml configuration. */
public final class RewardsConfig {

  private final List<Criterion> criterionPool;
  private final Reward dailyReward;
  private final String providerMode;
  private final String providerEndpoint;
  private final List<String> commandAllowlist;

  private RewardsConfig(
      List<Criterion> criterionPool,
      Reward dailyReward,
      String providerMode,
      String providerEndpoint,
      List<String> commandAllowlist) {
    this.criterionPool = List.copyOf(criterionPool);
    this.dailyReward = dailyReward;
    this.providerMode = providerMode;
    this.providerEndpoint = providerEndpoint;
    this.commandAllowlist = List.copyOf(commandAllowlist);
  }

  public static RewardsConfig load(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    File file = new File(plugin.getDataFolder(), "rewards.yml");
    if (!file.exists()) {
      plugin.saveResource("rewards.yml", false);
    }
    return parse(YamlConfiguration.loadConfiguration(file), plugin.getLogger());
  }

  static RewardsConfig parse(YamlConfiguration yaml, Logger logger) {
    Objects.requireNonNull(yaml, "yaml");
    Objects.requireNonNull(logger, "logger");
    Reward defaultReward = parseReward(yaml.getConfigurationSection("daily-reward"), logger);
    List<Criterion> pool = new ArrayList<>();
    for (MapEntry entry : entries(yaml.getMapList("criterion-pool"))) {
      try {
        pool.add(parseCriterion(entry.values(), defaultReward));
      } catch (IllegalArgumentException exception) {
        logger.warning("Skipping rewards.yml criterion: " + exception.getMessage());
      }
    }
    if (pool.isEmpty()) {
      pool.add(new LoginDaysCriterion("default-login", "Log in today", 1, defaultReward));
    }
    ConfigurationSection provider = yaml.getConfigurationSection("provider");
    String mode = provider == null ? "OPTIONAL" : provider.getString("mode", "OPTIONAL");
    String endpoint = provider == null ? "" : provider.getString("endpoint", "");
    List<String> allowlist = yaml.getStringList("command-allowlist");
    return new RewardsConfig(
        pool,
        defaultReward,
        mode.trim().toUpperCase(Locale.ROOT),
        endpoint.trim(),
        allowlist.stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).toList());
  }

  public List<Criterion> criterionPool() {
    return criterionPool;
  }

  public Reward dailyReward() {
    return dailyReward;
  }

  public String providerMode() {
    return providerMode;
  }

  public String providerEndpoint() {
    return providerEndpoint;
  }

  public List<String> commandAllowlist() {
    return commandAllowlist;
  }

  private static Criterion parseCriterion(java.util.Map<?, ?> values, Reward defaultReward) {
    String type = required(values, "type").toUpperCase(Locale.ROOT);
    String id = required(values, "id");
    String description = required(values, "description");
    int target = integer(values, "target");
    Reward reward = defaultReward;
    Object rewardObject = values.get("reward");
    if (rewardObject instanceof java.util.Map<?, ?> rewardValues) {
      reward = parseRewardValues(rewardValues);
    }
    return switch (type) {
      case "MINE_BLOCKS" ->
          new MineBlocksCriterion(
              id, description, MaterialKey.parse(required(values, "block")), target, reward);
      case "KILL_ENTITIES" ->
          new KillEntitiesCriterion(
              id, description, MaterialKey.parse(required(values, "entity")), target, reward);
      case "CRAFT_ITEMS" ->
          new CraftItemsCriterion(
              id, description, MaterialKey.parse(required(values, "item")), target, reward);
      case "GAIN_XP" -> new GainXpCriterion(id, description, target, reward);
      case "LOGIN_DAYS" -> new LoginDaysCriterion(id, description, target, reward);
      case "PLAY_TIME" -> new PlayTimeCriterion(id, description, target, reward);
      default -> throw new IllegalArgumentException("unknown criterion type " + type);
    };
  }

  private static Reward parseReward(ConfigurationSection section, Logger logger) {
    if (section == null) {
      return Reward.xp(100);
    }
    try {
      return new Reward(
          RewardType.valueOf(section.getString("type", "XP").toUpperCase(Locale.ROOT)),
          section.getString("payload", section.getString("item", "xp")),
          section.getInt("amount", section.getInt("count", 1)));
    } catch (IllegalArgumentException exception) {
      logger.warning("Invalid daily reward; using 100 XP: " + exception.getMessage());
      return Reward.xp(100);
    }
  }

  private static Reward parseRewardValues(java.util.Map<?, ?> values) {
    RewardType type = RewardType.valueOf(required(values, "type").toUpperCase(Locale.ROOT));
    Object payloadValue =
        values.containsKey("payload") ? values.get("payload") : values.get("item");
    String payload = payloadValue == null ? "xp" : String.valueOf(payloadValue);
    int amount = integer(values, values.containsKey("count") ? "count" : "amount");
    return new Reward(type, payload, amount);
  }

  private static String required(java.util.Map<?, ?> values, String key) {
    Object value = values.get(key);
    if (value == null || String.valueOf(value).trim().isEmpty()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return String.valueOf(value).trim();
  }

  private static int integer(java.util.Map<?, ?> values, String key) {
    Object value = values.get(key);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException(key + " must be numeric");
    }
    return number.intValue();
  }

  private static List<MapEntry> entries(List<java.util.Map<?, ?>> values) {
    return values.stream().map(MapEntry::new).toList();
  }

  private record MapEntry(java.util.Map<?, ?> values) {}
}
