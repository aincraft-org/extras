package dev.mintychochip.core;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for parties, memberships, and invitations.
 *
 * <p>Every mutation is atomic. {@link #close()} releases the backing store.
 */
interface PartyRepository extends AutoCloseable {

    Optional<Party> findById(UUID partyId);

    Optional<Party> findByMember(UUID playerId);

    List<PartyInvite> findPendingInvites(UUID playerId, Instant now);

    Optional<PartyInvite> findInvite(UUID partyId, UUID invitee, Instant now);

    /** All invitations for a party regardless of expiry (for leader hand-off). */
    List<PartyInvite> findPendingInvitesUnbounded(UUID partyId);

    void createParty(UUID partyId, String name, UUID leaderId, Instant createdAt);

    void deleteParty(UUID partyId);

    void addMember(UUID partyId, UUID memberId, Instant joinedAt);

    void removeMember(UUID partyId, UUID memberId);

    void setLeader(UUID partyId, UUID leaderId);

    void upsertInvite(UUID partyId, UUID invitee, UUID inviter, Instant expiresAt);

    /** Re-points the inviter of a party's invites from {@code oldLeader} to {@code newLeader}, atomically. */
    void reassignInviteInviter(UUID partyId, UUID oldLeader, UUID newLeader);

    void deleteInvite(UUID partyId, UUID invitee);

    void acceptInvite(UUID partyId, UUID invitee, Instant joinedAt);

    void leaderLeaves(UUID partyId, UUID oldLeader, UUID newLeader);

    @Override
    void close();
}
