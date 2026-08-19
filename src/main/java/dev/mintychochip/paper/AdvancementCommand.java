package dev.mintychochip.paper;

import dev.mintychochip.api.AdvancementResult;
import dev.mintychochip.api.AdvancementService;
import dev.mintychochip.api.CustomAdvancement;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /advancements} — operators grant custom advancements; players list their own completions.
 */
public final class AdvancementCommand implements BasicCommand {

  static final String ADMIN_PERMISSION = "extras.advancements.admin";
  static final String USE_PERMISSION = "extras.advancements.use";

  private final AdvancementService advancementService;

  public AdvancementCommand(AdvancementService advancementService) {
    this.advancementService = advancementService;
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (args.length == 0) {
      sendUsage(sender);
      return;
    }
    String action = args[0].toLowerCase(Locale.ROOT);
    switch (action) {
      case "grant" -> grant(sender, args);
      case "list" -> list(sender, args);
      default -> sendUsage(sender);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 1) {
      return filter(List.of("grant", "list"), args[0]);
    }
    if (args.length == 2 && "grant".equalsIgnoreCase(args[0])) {
      if (!stack.getSender().hasPermission(ADMIN_PERMISSION)) {
        return List.of();
      }
      return filter(onlinePlayerNames(), args[1]);
    }
    if (args.length == 3 && "grant".equalsIgnoreCase(args[0])) {
      if (!stack.getSender().hasPermission(ADMIN_PERMISSION)) {
        return List.of();
      }
      List<String> ids = new ArrayList<>();
      for (CustomAdvancement advancement : advancementService.catalog()) {
        ids.add(advancement.id());
      }
      return filter(ids, args[2]);
    }
    if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
      if (stack.getSender().hasPermission(ADMIN_PERMISSION)) {
        return filter(onlinePlayerNames(), args[1]);
      }
      return List.of();
    }
    return List.of();
  }

  private void grant(CommandSender sender, String... args) {
    if (!sender.hasPermission(ADMIN_PERMISSION)) {
      sender.sendMessage("You do not have permission to grant advancements.");
      return;
    }
    if (args.length < 3) {
      sender.sendMessage("Usage: /advancements grant <player> <id>");
      return;
    }
    UUID playerId = PlayerIds.resolvePlayerId(sender, args[1]);
    if (playerId == null) {
      return;
    }
    AdvancementResult result = advancementService.grant(playerId, args[2]);
    sender.sendMessage(
        switch (result) {
          case GRANTED -> "Granted " + args[2] + " to " + args[1] + ".";
          case ALREADY_COMPLETED -> args[1] + " already has " + args[2] + ".";
          case UNKNOWN_ADVANCEMENT -> "Unknown advancement: " + args[2];
        });
  }

  private void list(CommandSender sender, String... args) {
    UUID playerId;
    String name;
    if (args.length >= 2) {
      if (!sender.hasPermission(ADMIN_PERMISSION)) {
        sender.sendMessage("You do not have permission to view others' advancements.");
        return;
      }
      playerId = PlayerIds.resolvePlayerId(sender, args[1]);
      if (playerId == null) {
        return;
      }
      name = args[1];
    } else {
      if (!sender.hasPermission(USE_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
        sender.sendMessage("You do not have permission to list advancements.");
        return;
      }
      playerId = requireSelf(sender);
      if (playerId == null) {
        return;
      }
      name = sender.getName();
    }

    Set<String> completed = advancementService.completed(playerId);
    if (completed.isEmpty()) {
      sender.sendMessage(name + " has no custom advancements.");
      return;
    }
    sender.sendMessage(name + "'s advancements:");
    for (String id : completed) {
      String title = advancementService.find(id).map(CustomAdvancement::title).orElse(id);
      sender.sendMessage("  " + id + " — " + title);
    }
  }

  private UUID requireSelf(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can list their own advancements.");
      return null;
    }
    return player.getUniqueId();
  }

  private void sendUsage(CommandSender sender) {
    sender.sendMessage("Usage: /advancements grant <player> <id>");
    sender.sendMessage("       /advancements list [player]");
  }

  private static List<String> filter(List<String> candidates, String prefix) {
    String lower = prefix.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (String candidate : candidates) {
      if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
        result.add(candidate);
      }
    }
    return result;
  }

  private static List<String> onlinePlayerNames() {
    List<String> names = new ArrayList<>();
    for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
      names.add(player.getName());
    }
    return names;
  }
}
