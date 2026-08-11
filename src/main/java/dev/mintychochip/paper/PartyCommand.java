package dev.mintychochip.paper;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;
import dev.mintychochip.api.PartyResult;
import dev.mintychochip.api.PartyService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /party} — create persistent parties, invite members, accept/decline,
 * leave/kick, disband, transfer leadership, and list membership/presence.
 */
public final class PartyCommand implements BasicCommand {

    private final PartyService partyService;

    public PartyCommand(PartyService partyService) {
        this.partyService = partyService;
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
            case "create" -> create(sender, args);
            case "invite" -> invite(sender, args);
            case "accept" -> accept(sender, args);
            case "decline" -> decline(sender, args);
            case "leave" -> leave(sender);
            case "kick" -> kick(sender, args);
            case "disband" -> disband(sender);
            case "transfer" -> transfer(sender, args);
            case "list" -> list(sender);
            case "help" -> sendUsage(sender);
            default -> sendUsage(sender);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            return filter(List.of(
                    "create", "invite", "accept", "decline", "leave",
                    "kick", "disband", "transfer", "list", "help"), args[0]);
        }
        if (args.length == 2 && ("invite".equals(args[0]) || "kick".equals(args[0])
                || "transfer".equals(args[0]))) {
            CommandSender sender = stack.getSender();
            UUID self = sender instanceof Player player ? player.getUniqueId() : null;
            List<String> candidates = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getUniqueId().equals(self)) {
                    candidates.add(player.getName());
                }
            }
            return filter(candidates, args[1]);
        }
        if (args.length == 2 && ("accept".equals(args[0]) || "decline".equals(args[0]))) {
            CommandSender sender = stack.getSender();
            if (sender instanceof Player player) {
                List<String> partyIds = new ArrayList<>();
                for (PartyInvite invite : partyService.pendingInvitations(player.getUniqueId())) {
                    partyIds.add(invite.partyId().toString());
                }
                return filter(partyIds, args[1]);
            }
            return List.of();
        }
        return List.of();
    }

    private void create(CommandSender sender, String[] args) {
        UUID playerId = requirePlayer(sender);
        if (playerId == null) {
            return;
        }
        String name = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim() : null;
        PartyResult result = partyService.createParty(playerId, name);
        sender.sendMessage(describe(result, "party created", "create"));
    }

    private void invite(CommandSender sender, String[] args) {
        UUID actorId = requirePlayer(sender);
        if (actorId == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /party invite <player>");
            return;
        }
        UUID targetId = resolvePlayerId(sender, args[1]);
        if (targetId == null) {
            return;
        }
        PartyResult result = partyService.invite(actorId, targetId);
        sender.sendMessage(describe(result, "invite sent to " + args[1], "invite"));
    }

    private void accept(CommandSender sender, String[] args) {
        UUID playerId = requirePlayer(sender);
        if (playerId == null) {
            return;
        }
        if (args.length < 2) {
            List<PartyInvite> invites = partyService.pendingInvitations(playerId);
            if (invites.isEmpty()) {
                sender.sendMessage("You have no pending party invitations.");
                return;
            }
            PartyInvite latest = invites.get(0);
            PartyResult result = partyService.acceptInvite(playerId, latest.partyId());
            sender.sendMessage(describe(result, "invite accepted", "accept"));
            return;
        }
        UUID partyId = parsePartyId(sender, args[1]);
        if (partyId == null) {
            return;
        }
        PartyResult result = partyService.acceptInvite(playerId, partyId);
        sender.sendMessage(describe(result, "invite accepted", "accept"));
    }

    private void decline(CommandSender sender, String[] args) {
        UUID playerId = requirePlayer(sender);
        if (playerId == null) {
            return;
        }
        if (args.length < 2) {
            List<PartyInvite> invites = partyService.pendingInvitations(playerId);
            if (invites.isEmpty()) {
                sender.sendMessage("You have no pending party invitations.");
                return;
            }
            PartyInvite latest = invites.get(0);
            PartyResult result = partyService.declineInvite(playerId, latest.partyId());
            sender.sendMessage(describe(result, "invite declined", "decline"));
            return;
        }
        UUID partyId = parsePartyId(sender, args[1]);
        if (partyId == null) {
            return;
        }
        PartyResult result = partyService.declineInvite(playerId, partyId);
        sender.sendMessage(describe(result, "invite declined", "decline"));
    }

    private void leave(CommandSender sender) {
        UUID playerId = requirePlayer(sender);
        if (playerId == null) {
            return;
        }
        PartyResult result = partyService.leaveParty(playerId);
        sender.sendMessage(describe(result, "you left the party", "leave"));
    }

    private void kick(CommandSender sender, String[] args) {
        UUID actorId = requirePlayer(sender);
        if (actorId == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /party kick <member>");
            return;
        }
        UUID targetId = resolvePlayerId(sender, args[1]);
        if (targetId == null) {
            return;
        }
        PartyResult result = partyService.kick(actorId, targetId);
        sender.sendMessage(describe(result, args[1] + " kicked", "kick"));
    }

    private void disband(CommandSender sender) {
        UUID actorId = requirePlayer(sender);
        if (actorId == null) {
            return;
        }
        PartyResult result = partyService.disband(actorId);
        sender.sendMessage(describe(result, "party disbanded", "disband"));
    }

    private void transfer(CommandSender sender, String[] args) {
        UUID actorId = requirePlayer(sender);
        if (actorId == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /party transfer <member>");
            return;
        }
        UUID targetId = resolvePlayerId(sender, args[1]);
        if (targetId == null) {
            return;
        }
        PartyResult result = partyService.transferLeadership(actorId, targetId);
        sender.sendMessage(describe(result, "leadership transferred to " + args[1], "transfer"));
    }

    private void list(CommandSender sender) {
        UUID playerId = requirePlayer(sender);
        if (playerId == null) {
            return;
        }
        Optional<Party> party = partyService.partyOf(playerId);
        if (party.isEmpty()) {
            List<PartyInvite> invites = partyService.pendingInvitations(playerId);
            if (invites.isEmpty()) {
                sender.sendMessage("You are not in a party.");
            } else {
                sender.sendMessage("You have " + invites.size() + " pending invitation(s):");
                for (PartyInvite invite : invites) {
                    sender.sendMessage("  " + partyIdLabel(invite.partyId()) + " — expires " + invite.expiresAt());
                }
                sender.sendMessage("Use /party accept <party> to join.");
            }
            return;
        }
        Party p = party.get();
        sender.sendMessage((p.name() == null ? "Party" : "Party " + p.name())
                + " (" + p.memberIds().size() + " members):");
        for (UUID memberId : p.memberIds()) {
            String marker = p.isLeader(memberId) ? "*" : " ";
            String presence = PlayerIds.isOnline(memberId) ? "online" : "offline";
            sender.sendMessage("  " + marker + " " + PlayerIds.playerName(memberId) + " (" + presence + ")");
        }
    }

    private static String partyIdLabel(UUID partyId) {
        return partyId.toString().substring(0, 8);
    }
    private UUID requirePlayer(CommandSender sender) {
        return PlayerIds.requirePlayer(sender, "party");
    }

    private UUID parsePartyId(CommandSender sender, String arg) {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid party id: " + arg);
            return null;
        }
    }

    private UUID resolvePlayerId(CommandSender sender, String name) {
        return PlayerIds.resolvePlayerId(sender, name);
    }

    private static String describe(PartyResult result, String subject, String action) {
        return switch (result) {
            case SUCCESS -> subject;
            case NOT_IN_PARTY -> "You are not in a party.";
            case ALREADY_IN_PARTY -> "You are already in a party.";
            case TARGET_IN_PARTY -> "That player is already in a party.";
            case SELF_INVITE -> "You cannot invite yourself.";
            case ALREADY_INVITED -> "That player is already invited (invite refreshed).";
            case PARTY_FULL -> "The party is full.";
            case NOT_LEADER -> "Only the party leader can do that.";
            case NOT_A_MEMBER -> "That player is not a member of the party.";
            case SELF_KICK -> "You cannot kick yourself; use /party disband.";
            case NO_INVITE -> "No pending invitation for that party.";
            case INVALID_NAME -> "Invalid party name.";
        };
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
        sender.sendMessage("Usage: /party create [name]");
        sender.sendMessage("       /party invite <player>");
        sender.sendMessage("       /party accept [party] | decline [party]");
        sender.sendMessage("       /party leave | kick <member> | disband");
        sender.sendMessage("       /party transfer <member> | list | help");
    }
}
