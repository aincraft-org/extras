package dev.mintychochip.paper;

import dev.mintychochip.api.FriendRequest;
import dev.mintychochip.api.FriendResult;
import dev.mintychochip.api.FriendService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /friend} — send/accept/decline/cancel friend requests, remove friends, and list
 * friendships and pending requests with presence.
 */
public final class FriendCommand implements BasicCommand {

  private final FriendService friendService;

  public FriendCommand(FriendService friendService) {
    this.friendService = friendService;
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
      case "add", "request" -> add(sender, args);
      case "accept" -> accept(sender, args);
      case "decline", "deny" -> decline(sender, args);
      case "cancel" -> cancel(sender, args);
      case "remove" -> remove(sender, args);
      case "list" -> list(sender, args);
      case "requests" -> requests(sender);
      case "help" -> sendUsage(sender);
      default -> sendUsage(sender);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 1) {
      return filter(
          List.of(
              "add",
              "request",
              "accept",
              "decline",
              "deny",
              "cancel",
              "remove",
              "list",
              "requests",
              "help"),
          args[0]);
    }
    CommandSender sender = stack.getSender();
    UUID self = sender instanceof Player player ? player.getUniqueId() : null;
    if (self == null) {
      return List.of();
    }
    if (args.length == 2 && ("add".equals(args[0]) || "request".equals(args[0]))) {
      return filter(onlinePlayerNames(self), args[1]);
    }
    if (args.length == 2
        && (("accept".equals(args[0])) || ("decline".equals(args[0]) || "deny".equals(args[0])))) {
      return filter(requestersOf(friendService.incomingRequests(self)), args[1]);
    }
    if (args.length == 2 && "cancel".equals(args[0])) {
      return filter(targetsOf(friendService.outgoingRequests(self)), args[1]);
    }
    if (args.length == 2 && "remove".equals(args[0])) {
      return filter(friendNames(friendService.friendIdsOf(self)), args[1]);
    }
    return List.of();
  }

  // ------------------------------------------------------------------ add

  private void add(CommandSender sender, String[] args) {
    UUID requesterId = PlayerIds.requirePlayer(sender);
    if (requesterId == null) {
      return;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /friend add <player>");
      return;
    }
    UUID targetId = PlayerIds.resolvePlayerId(sender, args[1]);
    if (targetId == null) {
      return;
    }
    FriendResult result = friendService.sendRequest(requesterId, targetId);
    if (result == FriendResult.SUCCESS) {
      sender.sendMessage("Friend request sent to " + args[1] + ".");
      notify(targetId, "You have a friend request from " + sender.getName() + ". /friend list");
    } else {
      sender.sendMessage(describe(result));
    }
  }

  // ------------------------------------------------------------- accept

  private void accept(CommandSender sender, String[] args) {
    UUID recipientId = PlayerIds.requirePlayer(sender);
    if (recipientId == null) {
      return;
    }
    UUID requesterId = resolveRequester(sender, args, false);
    if (requesterId == null) {
      return;
    }
    FriendResult result = friendService.acceptRequest(recipientId, requesterId);
    if (result == FriendResult.SUCCESS) {
      String name = PlayerIds.playerName(requesterId);
      sender.sendMessage("You are now friends with " + name + ".");
      notify(requesterId, sender.getName() + " accepted your friend request.");
    } else {
      sender.sendMessage(describe(result));
    }
  }

  // ------------------------------------------------------------ decline

  private void decline(CommandSender sender, String[] args) {
    UUID recipientId = PlayerIds.requirePlayer(sender);
    if (recipientId == null) {
      return;
    }
    UUID requesterId = resolveRequester(sender, args, false);
    if (requesterId == null) {
      return;
    }
    FriendResult result = friendService.declineRequest(recipientId, requesterId);
    if (result == FriendResult.SUCCESS) {
      sender.sendMessage("Friend request from " + PlayerIds.playerName(requesterId) + " declined.");
    } else {
      sender.sendMessage(describe(result));
    }
  }

  // ------------------------------------------------------------- cancel

  private void cancel(CommandSender sender, String[] args) {
    UUID requesterId = PlayerIds.requirePlayer(sender);
    if (requesterId == null) {
      return;
    }
    UUID targetId = resolveRequester(sender, args, true);
    if (targetId == null) {
      return;
    }
    FriendResult result = friendService.cancelRequest(requesterId, targetId);
    if (result == FriendResult.SUCCESS) {
      sender.sendMessage("Friend request to " + PlayerIds.playerName(targetId) + " cancelled.");
    } else {
      sender.sendMessage(describe(result));
    }
  }

  // ------------------------------------------------------------- remove

  private void remove(CommandSender sender, String[] args) {
    UUID actorId = PlayerIds.requirePlayer(sender);
    if (actorId == null) {
      return;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /friend remove <player>");
      return;
    }
    UUID targetId = PlayerIds.resolvePlayerId(sender, args[1]);
    if (targetId == null) {
      return;
    }
    FriendResult result = friendService.removeFriend(actorId, targetId);
    if (result == FriendResult.SUCCESS) {
      String name = PlayerIds.playerName(targetId);
      sender.sendMessage("You are no longer friends with " + name + ".");
      notify(targetId, sender.getName() + " removed you as a friend.");
    } else {
      sender.sendMessage(describe(result));
    }
  }

  // --------------------------------------------------------------- list

  private void list(CommandSender sender, String[] args) {
    UUID playerId = PlayerIds.requirePlayer(sender);
    if (playerId == null) {
      return;
    }
    List<UUID> friendIds = friendService.friendIdsOf(playerId);
    if (friendIds.isEmpty()) {
      sender.sendMessage("You have no friends yet. Use /friend add <player> to send a request.");
      return;
    }
    sender.sendMessage(friendIds.size() + " friend(s):");
    for (UUID friendId : friendIds) {
      String presence = PlayerIds.isOnline(friendId) ? "online" : "offline";
      sender.sendMessage("  " + PlayerIds.playerName(friendId) + " (" + presence + ")");
    }
  }

  // ----------------------------------------------------------- requests

  private void requests(CommandSender sender) {
    UUID playerId = PlayerIds.requirePlayer(sender);
    if (playerId == null) {
      return;
    }
    int sent = showOutgoing(sender, playerId);
    int pending = showIncoming(sender, playerId);
    if (sent + pending == 0) {
      sender.sendMessage("You have no pending friend requests.");
    }
  }

  private int showOutgoing(CommandSender sender, UUID playerId) {
    List<FriendRequest> outgoing = friendService.outgoingRequests(playerId);
    if (outgoing.isEmpty()) {
      return 0;
    }
    sender.sendMessage(outgoing.size() + " outgoing request(s):");
    for (FriendRequest request : outgoing) {
      sender.sendMessage("  → " + PlayerIds.playerName(request.targetId()));
    }
    return outgoing.size();
  }

  private int showIncoming(CommandSender sender, UUID playerId) {
    List<FriendRequest> incoming = friendService.incomingRequests(playerId);
    if (incoming.isEmpty()) {
      return 0;
    }
    sender.sendMessage(incoming.size() + " incoming request(s):");
    for (FriendRequest request : incoming) {
      sender.sendMessage("  ← " + PlayerIds.playerName(request.requesterId()));
    }
    sender.sendMessage("Use /friend accept <player> or /friend decline <player> to respond.");
    return incoming.size();
  }

  // ------------------------------------------------------------ helpers

  /**
   * Resolves the requester for accept/decline (incoming) or the target for cancel (outgoing). With
   * no argument, falls back to the latest pending request in that direction.
   */
  private UUID resolveRequester(CommandSender sender, String[] args, boolean outgoing) {
    if (args.length < 2) {
      List<FriendRequest> requests =
          outgoing
              ? friendService.outgoingRequests(PlayerIds.requirePlayer(sender))
              : friendService.incomingRequests(PlayerIds.requirePlayer(sender));
      if (requests.isEmpty()) {
        sender.sendMessage(
            outgoing
                ? "You have no pending outgoing friend requests."
                : "You have no pending incoming friend requests.");
        return null;
      }
      FriendRequest latest = requests.get(requests.size() - 1);
      return outgoing ? latest.targetId() : latest.requesterId();
    }
    return PlayerIds.resolvePlayerId(sender, args[1]);
  }

  private static String describe(FriendResult result) {
    return switch (result) {
      case SUCCESS -> "Request sent.";
      case SELF_REQUEST -> "You cannot add yourself as a friend.";
      case ALREADY_FRIENDS -> "You are already friends with that player.";
      case REQUEST_EXISTS -> "A friend request between you and that player is already pending.";
      case NO_REQUEST -> "No pending friend request for that player.";
      case NOT_FRIENDS -> "That player is not on your friend list.";
    };
  }

  private static List<String> onlinePlayerNames(UUID self) {
    List<String> names = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!player.getUniqueId().equals(self)) {
        names.add(player.getName());
      }
    }
    return names;
  }

  private static List<String> requestersOf(List<FriendRequest> requests) {
    List<String> names = new ArrayList<>();
    for (FriendRequest request : requests) {
      names.add(PlayerIds.playerName(request.requesterId()));
    }
    return names;
  }

  private static List<String> targetsOf(List<FriendRequest> requests) {
    List<String> names = new ArrayList<>();
    for (FriendRequest request : requests) {
      names.add(PlayerIds.playerName(request.targetId()));
    }
    return names;
  }

  private static List<String> friendNames(List<UUID> friendIds) {
    List<String> names = new ArrayList<>();
    for (UUID friendId : friendIds) {
      names.add(PlayerIds.playerName(friendId));
    }
    return names;
  }

  private static void notify(UUID playerId, String message) {
    Player player = Bukkit.getPlayer(playerId);
    if (player != null && player.isOnline()) {
      player.sendMessage(message);
    }
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

  private void sendUsage(CommandSender sender) {
    sender.sendMessage("Usage: /friend request <player>");
    sender.sendMessage("       /friend accept <player> | decline <player> | cancel <player>");
    sender.sendMessage("       /friend remove <player> | list | requests | help");
  }
}
