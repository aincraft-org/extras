package dev.mintychochip.paper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Shared player resolution helpers for party/friend commands.
 */
final class PlayerIds {

    private PlayerIds() {
    }

    /** Returns the sender's UUID, or {@code null} (after messaging) if not a player. */
    static UUID requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return null;
        }
        return player.getUniqueId();
    }

    /** Returns the sender's UUID with a command-specific error message. */
    static UUID requirePlayer(CommandSender sender, String commandName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use " + commandName + " commands.");
            return null;
        }
        return player.getUniqueId();
    }

    /**
     * Resolves a player name to a UUID: online players first, then offline
     * mode name-derived UUIDs, then previously-seen offline players. Messages
     * and returns {@code null} when unresolvable.
     */
    static UUID resolvePlayerId(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        if (!Bukkit.getOnlineMode()) {
            // Offline-mode servers key players by name-derived UUID.
            return uuidFromName(name);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getUniqueId() != null && offline.hasPlayedBefore()) {
            return offline.getUniqueId();
        }
        sender.sendMessage("Unknown player: " + name);
        return null;
    }

    /** Best-effort deterministic UUID for a player name (offline-mode servers). */
    static UUID uuidFromName(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    static boolean isOnline(UUID playerId) {
        return Bukkit.getPlayer(playerId) != null;
    }

    static String playerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.getName() != null ? offline.getName() : playerId.toString().substring(0, 8);
    }
}
