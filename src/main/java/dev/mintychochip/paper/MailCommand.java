package dev.mintychochip.paper;

import dev.mintychochip.api.MailService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /mail} — player mailbox: send, read, delete, clear, and open the GUI.
 *
 * <p>The command is registered by {@link dev.mintychochip.ExtrasPlugin}; the GUI
 * (mailbox/compose) is opened by {@link MailboxGui} and {@link ComposeGui}.
 */
public final class MailCommand implements BasicCommand {

    private static final String USE_PERMISSION = "extras.mail.use";
    private static final long CLEAR_CONFIRM_TTL_MILLIS = 60_000L;

    private final MailService mailService;
    private final Map<UUID, Long> clearConfirmations = new ConcurrentHashMap<>();

    public MailCommand(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /mail.");
            return;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage("You do not have permission to use mail.");
            return;
        }
        if (args.length == 0) {
            MailboxGui.open(player, mailService);
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "open" -> MailboxGui.open(player, mailService);
            case "send" -> send(player, args);
            case "delete" -> delete(player, args);
            case "clear" -> clear(player);
            default -> sendUsage(player, "mail");
        }
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender instanceof Player && sender.hasPermission(USE_PERMISSION);
    }

    private boolean send(Player player, String... args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /mail send <player> <message…>");
            return true;
        }
        UUID recipientId = resolvePlayerId(player, args[1]);
        if (recipientId == null) {
            return true;
        }
        String body = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
        if (body.isEmpty()) {
            player.sendMessage("Message cannot be empty.");
            return true;
        }
        ComposeGui.open(player, mailService, recipientId, args[1], body);
        return true;
    }

    private boolean delete(Player player, String... args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /mail delete <id>");
            return true;
        }
        long mailId;
        try {
            mailId = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid mail id: " + args[1]);
            return true;
        }
        boolean deleted = mailService.delete(player.getUniqueId(), mailId);
        player.sendMessage(deleted ? "Mail #" + mailId + " deleted." : "No mail #" + mailId + " in your mailbox.");
        return true;
    }

    private boolean clear(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = clearConfirmations.get(playerId);
        if (last == null || now - last > CLEAR_CONFIRM_TTL_MILLIS) {
            clearConfirmations.put(playerId, now);
            player.sendMessage("Type /mail clear again within 60s to delete ALL read mail.");
            return true;
        }
        clearConfirmations.remove(playerId);
        int deleted = mailService.deleteAllRead(playerId);
        player.sendMessage(deleted == 0 ? "No read mail to delete." : "Deleted " + deleted + " read mail message(s).");
        return true;
    }

    /** Matches {@link TitleCommand} resolution: online player, else offline name-UUID. */
    private UUID resolvePlayerId(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        if (!Bukkit.getOnlineMode()) {
            return uuidFromName(name);
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getUniqueId() != null && offline.hasPlayedBefore()) {
            return offline.getUniqueId();
        }
        sender.sendMessage("Unknown player: " + name + " (online mode; must be online or have joined before)");
        return null;
    }

    /** Best-effort deterministic UUID for a player name (offline-mode servers). */
    private static UUID uuidFromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " [open]");
        sender.sendMessage("       /" + label + " send <player> <message…>");
        sender.sendMessage("       /" + label + " delete <id>");
        sender.sendMessage("       /" + label + " clear");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            return filter(List.of("send", "delete", "clear", "open"), args[0]);
        }
        if (args.length == 2 && "send".equalsIgnoreCase(args[0])) {
            return filter(onlinePlayerNames(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(java.util.Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }
}
