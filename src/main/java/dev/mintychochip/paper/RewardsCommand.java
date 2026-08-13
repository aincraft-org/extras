package dev.mintychochip.paper;

import dev.mintychochip.api.rewards.Criterion;
import dev.mintychochip.api.rewards.DailyRewardClaim;
import dev.mintychochip.api.rewards.DailyRewardService;
import dev.mintychochip.api.rewards.LeaderboardEntry;
import dev.mintychochip.api.rewards.LeaderboardPeriod;
import dev.mintychochip.api.rewards.LeaderboardService;
import dev.mintychochip.api.rewards.LoginStreakService;
import dev.mintychochip.api.rewards.Reward;
import dev.mintychochip.api.rewards.RewardType;
import dev.mintychochip.api.rewards.StreakSnapshot;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Commands for daily rewards, login streaks, and leaderboards. */
public final class RewardsCommand implements BasicCommand {

  static final String USE_PERMISSION = "extras.rewards.use";
  static final String ADMIN_PERMISSION = "extras.rewards.admin";

  private final DailyRewardService dailyRewardService;
  private final LeaderboardService leaderboardService;
  private final LoginStreakService streakService;
  private final List<Criterion> criterionPool;
  private final List<String> commandAllowlist;

  public RewardsCommand(
      DailyRewardService dailyRewardService,
      LeaderboardService leaderboardService,
      LoginStreakService streakService,
      List<Criterion> criterionPool,
      List<String> commandAllowlist) {
    this.dailyRewardService = Objects.requireNonNull(dailyRewardService, "dailyRewardService");
    this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService");
    this.streakService = Objects.requireNonNull(streakService, "streakService");
    this.criterionPool = List.copyOf(criterionPool);
    this.commandAllowlist =
        commandAllowlist.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (!sender.hasPermission(USE_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
      sender.sendMessage("You do not have permission to use rewards.");
      return;
    }
    String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
    switch (action) {
      case "status" -> status(sender);
      case "claim" -> claim(sender);
      case "streak" -> streak(sender);
      case "top", "leaderboard" -> leaderboard(sender, args);
      case "reroll" -> reroll(sender);
      default -> usage(sender);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 1) {
      return List.of("status", "claim", "streak", "top", "reroll").stream()
          .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
          .toList();
    }
    if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
      return List.of("daily", "weekly").stream()
          .filter(value -> value.startsWith(args[1]))
          .toList();
    }
    return List.of();
  }

  private void status(CommandSender sender) {
    UUID playerId = PlayerIds.requirePlayer(sender, "/rewards");
    if (playerId == null) return;
    var status = dailyRewardService.status(playerId);
    sender.sendMessage(
        "Today's reward: "
            + status.criterion().criterion().description()
            + " ("
            + status.progress()
            + "/"
            + status.criterion().criterion().target()
            + ")"
            + (status.claimed() ? " [claimed]" : ""));
  }

  private void claim(CommandSender sender) {
    UUID playerId = PlayerIds.requirePlayer(sender, "/rewards claim");
    if (playerId == null) return;
    DailyRewardClaim claim = dailyRewardService.claim(playerId);
    if (claim.result() != dev.mintychochip.api.rewards.DailyRewardResult.CLAIMED) {
      sender.sendMessage("Reward claim: " + claim.result().name().toLowerCase(Locale.ROOT) + ".");
      return;
    }
    executeReward(sender, claim.reward());
    sender.sendMessage("Daily reward claimed.");
  }

  private void streak(CommandSender sender) {
    UUID playerId = PlayerIds.requirePlayer(sender, "/streak");
    if (playerId == null) return;
    StreakSnapshot streak = streakService.streak(playerId);
    sender.sendMessage(
        "Login streak: " + streak.currentStreak() + " days (best " + streak.bestStreak() + ").");
  }

  private void leaderboard(CommandSender sender, String[] args) {
    LeaderboardPeriod period =
        args.length > 1 && "weekly".equalsIgnoreCase(args[1])
            ? LeaderboardPeriod.WEEKLY
            : LeaderboardPeriod.DAILY;
    sender.sendMessage(period.name() + " leaderboard:");
    for (LeaderboardEntry entry : leaderboardService.leaderboard(period, 10).entries()) {
      sender.sendMessage(
          entry.rank() + ". " + PlayerIds.playerName(entry.playerId()) + " — " + entry.score());
    }
  }

  private void reroll(CommandSender sender) {
    if (!sender.hasPermission(ADMIN_PERMISSION)) {
      sender.sendMessage("You do not have permission to reroll rewards.");
      return;
    }
    if (criterionPool.isEmpty()) {
      sender.sendMessage("No criterion pool is configured.");
      return;
    }
    dailyRewardService.forceCriterion(criterionPool.get(0));
    sender.sendMessage("Today's criterion was rerolled.");
  }

  private void executeReward(CommandSender sender, Reward reward) {
    if (reward.type() == RewardType.XP && sender instanceof Player player) {
      player.giveExp(reward.amount());
    } else if (reward.type() == RewardType.ITEM && sender instanceof Player player) {
      Material material =
          Material.matchMaterial(
              reward.payload().split(":", 2)[reward.payload().contains(":") ? 1 : 0]);
      if (material != null) player.getInventory().addItem(new ItemStack(material, reward.amount()));
    } else if (reward.type() == RewardType.COMMAND) {
      String root = reward.payload().trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
      if (commandAllowlist.contains(root)) {
        Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(), reward.payload().replace("%player%", sender.getName()));
      } else {
        sender.sendMessage("Configured reward command is not allow-listed.");
      }
    }
  }

  private static void usage(CommandSender sender) {
    sender.sendMessage("Usage: /rewards [status|claim|streak|top [daily|weekly]|reroll]");
  }
}
