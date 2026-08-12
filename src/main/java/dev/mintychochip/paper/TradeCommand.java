package dev.mintychochip.paper;

import dev.mintychochip.api.TradeResult;
import dev.mintychochip.api.TradeService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /trade} — request, accept, decline, cancel, and complete item trades. */
public final class TradeCommand implements BasicCommand {
  private final TradeService tradeService;

  public TradeCommand(TradeService tradeService) {
    this.tradeService = tradeService;
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use /trade.");
      return;
    }
    if (args.length == 0) {
      sendUsage(player);
      return;
    }
    if (args.length == 1 && !isAction(args[0])) {
      request(player, args[0]);
      return;
    }
    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "request", "send" -> request(player, args.length > 1 ? args[1] : null);
      case "accept" -> accept(player);
      case "decline", "deny" -> decline(player);
      case "cancel" -> cancel(player);
      default -> sendUsage(player);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 1) {
      List<String> candidates =
          new ArrayList<>(List.of("accept", "decline", "deny", "cancel", "request", "send"));
      candidates.addAll(
          onlinePlayerNames(
              stack.getSender() instanceof Player player ? player.getUniqueId() : null));
      return filter(candidates, args[0]);
    }
    if (args.length == 2
        && (args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("send"))) {
      return filter(
          onlinePlayerNames(
              stack.getSender() instanceof Player player ? player.getUniqueId() : null),
          args[1]);
    }
    return List.of();
  }

  private void request(Player player, String targetName) {
    if (targetName == null || targetName.isBlank()) {
      player.sendMessage("Usage: /trade <player>");
      return;
    }
    UUID targetId = PlayerIds.resolvePlayerId(player, targetName);
    if (targetId == null) {
      return;
    }
    TradeResult result = tradeService.request(player.getUniqueId(), targetId);
    if (result == TradeResult.SUCCESS) {
      player.sendMessage(ChatColor.GREEN + "Trade request sent to " + targetName + ".");
      Player target = Bukkit.getPlayer(targetId);
      if (target != null) {
        target.sendMessage(
            ChatColor.YELLOW
                + player.getName()
                + " wants to trade. Use /trade accept or /trade decline.");
      }
    } else {
      player.sendMessage(ChatColor.RED + describe(result));
    }
  }

  private static boolean isAction(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "request", "send", "accept", "decline", "deny", "cancel" -> true;
      default -> false;
    };
  }

  private void accept(Player player) {
    UUID playerId = player.getUniqueId();
    UUID requesterId = tradeService.pendingRequestFrom(playerId).orElse(null);
    if (requesterId == null) {
      player.sendMessage(ChatColor.RED + "You have no pending trade requests.");
      return;
    }
    TradeResult result = tradeService.accept(playerId);
    if (result != TradeResult.SUCCESS) {
      player.sendMessage(ChatColor.RED + describe(result));
      return;
    }
    Player requester = Bukkit.getPlayer(requesterId);
    if (requester == null) {
      tradeService.cancel(playerId);
      player.sendMessage(ChatColor.RED + "The requester is no longer online.");
      return;
    }
    TradeGui.open(requester, player, tradeService);
  }

  private void decline(Player player) {
    TradeResult result = tradeService.decline(player.getUniqueId());
    player.sendMessage(
        result == TradeResult.SUCCESS ? "Trade request declined." : describe(result));
  }

  private void cancel(Player player) {
    TradeResult result = tradeService.cancel(player.getUniqueId());
    player.sendMessage(result == TradeResult.SUCCESS ? "Trade cancelled." : describe(result));
  }

  private static List<String> onlinePlayerNames(UUID self) {
    List<String> names = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (self == null || !player.getUniqueId().equals(self)) {
        names.add(player.getName());
      }
    }
    return names;
  }

  private static String describe(TradeResult result) {
    return switch (result) {
      case SELF_TRADE -> "You cannot trade with yourself.";
      case REQUEST_EXISTS -> "A trade request is already pending.";
      case ALREADY_TRADING -> "One of you is already in a trade.";
      case NO_REQUEST -> "You have no pending trade request.";
      case NOT_PARTICIPANT -> "You are not in an active trade.";
      default -> "The trade request could not be completed.";
    };
  }

  private static List<String> filter(List<String> candidates, String prefix) {
    String lower = prefix.toLowerCase(Locale.ROOT);
    return candidates.stream()
        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
        .toList();
  }

  private static void sendUsage(CommandSender sender) {
    sender.sendMessage("Usage: /trade <player>");
    sender.sendMessage("       /trade request <player>");
    sender.sendMessage("       /trade accept | decline | cancel");
  }
}
