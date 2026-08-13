package dev.mintychochip.paper;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import dev.mintychochip.api.ChatResult;
import dev.mintychochip.api.ChatService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /chat} preference and message-channel command. */
public final class ChatCommand implements BasicCommand {
  private static final String USE_PERMISSION = "extras.chat.use";
  private static final List<String> ACTIONS =
      List.of("send", "mute", "unmute", "status", "channels", "help");

  @FunctionalInterface
  public interface OneShotSender {
    void send(Player player, ChannelId channel, String message);
  }

  private final ChatService chatService;
  private final OneShotSender oneShotSender;

  public ChatCommand(ChatService chatService, OneShotSender oneShotSender) {
    this.chatService = java.util.Objects.requireNonNull(chatService);
    this.oneShotSender = java.util.Objects.requireNonNull(oneShotSender);
  }

  enum Action {
    SELECT,
    SEND,
    MUTE,
    UNMUTE,
    STATUS,
    CHANNELS,
    HELP,
    UNKNOWN
  }

  static Action parseAction(String[] args) {
    if (args.length == 0) return Action.HELP;
    return switch (args[0].toLowerCase(Locale.ROOT)) {
      case "send" -> Action.SEND;
      case "mute" -> Action.MUTE;
      case "unmute" -> Action.UNMUTE;
      case "status" -> Action.STATUS;
      case "channels" -> Action.CHANNELS;
      case "help" -> Action.HELP;
      default -> ChannelId.parse(args[0]).isPresent() ? Action.SELECT : Action.UNKNOWN;
    };
  }

  static Optional<ChannelId> parseChannel(String[] args, int index) {
    return args.length > index ? ChannelId.parse(args[index]) : Optional.empty();
  }

  static String message(String[] args, int start) {
    return args.length <= start
        ? ""
        : String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
  }

  static List<String> suggestions(String input) {
    String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
    return java.util.stream.Stream.concat(
            ACTIONS.stream(), Arrays.stream(ChannelId.values()).map(ChannelId::key))
        .filter(value -> value.startsWith(prefix))
        .sorted()
        .toList();
  }

  static void sendOnce(
      ChatService service,
      UUID playerId,
      Player player,
      ChannelId channel,
      String message,
      OneShotSender sender) {
    java.util.Objects.requireNonNull(service);
    java.util.Objects.requireNonNull(playerId);
    sender.send(player, channel, message);
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use /chat.");
      return;
    }
    if (!player.hasPermission(USE_PERMISSION)) {
      player.sendMessage("You do not have permission to use chat.");
      return;
    }
    UUID playerId = player.getUniqueId();
    switch (parseAction(args)) {
      case SELECT -> select(player, args, playerId);
      case SEND -> send(player, args, playerId);
      case MUTE -> preferenceMutation(player, args, playerId, true);
      case UNMUTE -> preferenceMutation(player, args, playerId, false);
      case STATUS -> status(player, playerId);
      case CHANNELS -> channels(player);
      case HELP, UNKNOWN -> help(player);
    }
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return sender instanceof Player && sender.hasPermission(USE_PERMISSION);
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 0 || (args.length == 1 && !"send".equalsIgnoreCase(args[0])))
      return suggestions(args.length == 0 ? "" : args[0]);
    if (args.length == 2
        && ("send".equalsIgnoreCase(args[0])
            || "mute".equalsIgnoreCase(args[0])
            || "unmute".equalsIgnoreCase(args[0]))) return channelSuggestions(args[1]);
    if (args.length == 2 && "select".equalsIgnoreCase(args[0])) return channelSuggestions(args[1]);
    return List.of();
  }

  private Collection<String> channelSuggestions(String input) {
    String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
    return Arrays.stream(ChannelId.values())
        .map(ChannelId::key)
        .filter(value -> value.startsWith(prefix))
        .sorted()
        .collect(Collectors.toList());
  }

  static String permission(ChannelId channel) {
    return "extras.chat.channel." + channel.key();
  }

  private static boolean permitted(Player player, ChannelId channel) {
    return player.hasPermission(permission(channel));
  }

  private void select(Player player, String[] args, UUID id) {
    ChannelId channel = parseChannel(args, 0).orElse(null);
    if (channel == null) {
      help(player);
      return;
    }
    if (!permitted(player, channel)) {
      player.sendMessage("You do not have permission to use that channel.");
      return;
    }
    player.sendMessage(
        chatService.select(id, channel) == ChatResult.SUCCESS
            ? "Chat channel set to " + channel.key() + "."
            : "Could not select that chat channel.");
  }

  private void send(Player player, String[] args, UUID id) {
    ChannelId channel = parseChannel(args, 1).orElse(null);
    if (args.length < 3 || channel == null || message(args, 2).isEmpty()) {
      player.sendMessage("Usage: /chat send <channel> <message>");
      return;
    }
    if (!permitted(player, channel)) {
      player.sendMessage("You do not have permission to use that channel.");
      return;
    }
    sendOnce(chatService, id, player, channel, message(args, 2), oneShotSender);
  }

  private void preferenceMutation(Player player, String[] args, UUID id, boolean mute) {
    ChannelId channel = parseChannel(args, 1).orElse(null);
    if (channel == null) {
      player.sendMessage("Usage: /chat " + (mute ? "mute" : "unmute") + " <channel>");
      return;
    }
    if (!permitted(player, channel)) {
      player.sendMessage("You do not have permission to use that channel.");
      return;
    }
    player.sendMessage(
        describe(mute ? chatService.mute(id, channel) : chatService.unmute(id, channel), mute));
  }

  private void status(Player player, UUID id) {
    ChannelPreferences preferences = chatService.preferences(id);
    String muted =
        preferences.mutedChannels().stream()
            .map(ChannelId::key)
            .sorted()
            .collect(Collectors.joining(", "));
    player.sendMessage("Active channel: " + preferences.activeChannel().key() + ".");
    player.sendMessage("Muted channels: " + (muted.isEmpty() ? "none" : muted) + ".");
  }

  private void channels(Player player) {
    player.sendMessage(
        "Chat channels: "
            + Arrays.stream(ChannelId.values())
                .map(ChannelId::key)
                .collect(Collectors.joining(", "))
            + ".");
  }

  private void help(Player player) {
    player.sendMessage(
        "Usage: /chat <channel> | /chat send <channel> <message> | /chat mute|unmute <channel> | /chat status | /chat channels | /chat help");
  }

  private static String describe(ChatResult result, boolean mute) {
    return switch (result) {
      case SUCCESS -> mute ? "Chat channel muted." : "Chat channel unmuted.";
      case GLOBAL_CANNOT_BE_MUTED -> "Global chat cannot be muted.";
      case ALREADY_MUTED -> "That channel is already muted.";
      case NOT_MUTED -> "That channel is not muted.";
    };
  }
}
