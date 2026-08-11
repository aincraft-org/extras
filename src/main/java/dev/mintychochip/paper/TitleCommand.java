package dev.mintychochip.paper;

import dev.mintychochip.api.TitleResult;
import dev.mintychochip.api.TitleService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /title} — admin grants/revokes cosmetic titles; players equip,
 * unequip, and list their own.
 */
public final class TitleCommand implements BasicCommand {

    static final String ADMIN_PERMISSION = "extras.titles.admin";

    private final TitleService titleService;

    public TitleCommand(TitleService titleService) {
        this.titleService = titleService;
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
            case "revoke" -> revoke(sender, args);
            case "equip" -> equip(sender, args);
            case "unequip" -> unequip(sender);
            case "list" -> list(sender, args);
            default -> sendUsage(sender);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            return filter(List.of("grant", "revoke", "equip", "unequip", "list"), args[0]);
        }
        if (args.length == 2
                && ("grant".equals(args[0]) || "revoke".equals(args[0]))) {
            CommandSender sender = stack.getSender();
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                return List.of();
            }
            return filter(onlinePlayerNames(), args[1]);
        }
        if (args.length == 2 && "list".equals(args[0])) {
            CommandSender sender = stack.getSender();
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                return filter(onlinePlayerNames(), args[1]);
            }
            return List.of();
        }
        return List.of();
    }

    private void grant(CommandSender sender, String... args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to grant titles.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /title grant <player> <title…>");
            return;
        }
        UUID playerId = PlayerIds.resolvePlayerId(sender, args[1]);
        if (playerId == null) {
            return;
        }
        TitleResult result = titleService.grantTitle(playerId, joinTitle(args, 2));
        sender.sendMessage(describe(result, args[1], "granted"));
    }

    private void revoke(CommandSender sender, String... args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to revoke titles.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /title revoke <player> <title…>");
            return;
        }
        UUID playerId = PlayerIds.resolvePlayerId(sender, args[1]);
        if (playerId == null) {
            return;
        }
        TitleResult result = titleService.revokeTitle(playerId, joinTitle(args, 2));
        sender.sendMessage(describe(result, args[1], "revoked"));
    }

    private void equip(CommandSender sender, String... args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /title equip <title…>");
            return;
        }
        UUID playerId = requireSelf(sender);
        if (playerId == null) {
            return;
        }
        TitleResult result = titleService.equipTitle(playerId, joinTitle(args, 1));
        sender.sendMessage(describe(result, "your title", "equipped"));
    }

    private void unequip(CommandSender sender) {
        UUID playerId = requireSelf(sender);
        if (playerId == null) {
            return;
        }
        TitleResult result = titleService.unequipTitle(playerId);
        sender.sendMessage(describe(result, "your title", "unequipped"));
    }

    private void list(CommandSender sender, String... args) {
        UUID playerId;
        String name;
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage("You do not have permission to view others' titles.");
                return;
            }
            playerId = PlayerIds.resolvePlayerId(sender, args[1]);
            if (playerId == null) {
                return;
            }
            name = args[1];
        } else {
            playerId = requireSelf(sender);
            if (playerId == null) {
                return;
            }
            name = sender.getName();
        }

        Set<String> unlocked = titleService.unlockedTitles(playerId);
        Optional<String> equipped = titleService.equippedTitle(playerId);
        if (unlocked.isEmpty()) {
            sender.sendMessage(name + " has no titles.");
            return;
        }
        sender.sendMessage(name + "'s titles:");
        for (String title : unlocked) {
            sender.sendMessage((equipped.filter(title::equals).isPresent() ? "> " : "  ") + title);
        }
    }

    private UUID requireSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can manage their own titles.");
            return null;
        }
        return player.getUniqueId();
    }

    private static String joinTitle(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private static String describe(TitleResult result, String subject, String action) {
        return switch (result) {
            case SUCCESS -> "Title " + action + ": " + subject;
            case ALREADY_UNLOCKED -> "That title is already unlocked for " + subject + ".";
            case NOT_UNLOCKED -> "That title is not unlocked for " + subject + ".";
            case INVALID_TITLE -> "Invalid title (must be 1–64 non-control characters).";
        };
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /title grant|revoke <player> <title…>");
        sender.sendMessage("       /title equip|unequip");
        sender.sendMessage("       /title list [player]");
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
