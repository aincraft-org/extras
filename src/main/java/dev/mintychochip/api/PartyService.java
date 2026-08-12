package dev.mintychochip.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read/write surface for persistent player parties.
 *
 * <p>A party has exactly one leader and an ordered membership; membership requires accepting a
 * pending invitation. Parties persist across restarts via SQLite. All read views are immutable
 * snapshots.
 */
public interface PartyService {

  PartyResult createParty(UUID playerId, String name);

  PartyResult invite(UUID actorId, UUID targetId);

  PartyResult acceptInvite(UUID playerId, UUID partyId);

  PartyResult declineInvite(UUID playerId, UUID partyId);

  PartyResult leaveParty(UUID playerId);

  PartyResult kick(UUID actorId, UUID targetId);

  PartyResult disband(UUID actorId);

  PartyResult transferLeadership(UUID actorId, UUID targetId);

  Optional<Party> partyOf(UUID playerId);

  List<PartyInvite> pendingInvitations(UUID playerId);
}
