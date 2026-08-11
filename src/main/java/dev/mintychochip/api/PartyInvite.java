package dev.mintychochip.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of a pending party invitation.
 *
 * <p>An invite is scoped to exactly one {@code partyId} and one invitee
 * ({@code targetId}). It carries the inviter and the point at which it expires.
 */
public final class PartyInvite {

    private final UUID partyId;
    private final UUID inviterId;
    private final UUID targetId;
    private final Instant expiresAt;

    public PartyInvite(UUID partyId, UUID inviterId, UUID targetId, Instant expiresAt) {
        this.partyId = Objects.requireNonNull(partyId, "partyId");
        this.inviterId = Objects.requireNonNull(inviterId, "inviterId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public UUID partyId() {
        return partyId;
    }

    public UUID inviterId() {
        return inviterId;
    }

    public UUID targetId() {
        return targetId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    /** True when the invite has passed its expiry and can no longer be accepted. */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt) || now.equals(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PartyInvite that)) {
            return false;
        }
        return partyId.equals(that.partyId)
                && inviterId.equals(that.inviterId)
                && targetId.equals(that.targetId)
                && expiresAt.equals(that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partyId, inviterId, targetId, expiresAt);
    }

    @Override
    public String toString() {
        return "PartyInvite{partyId=" + partyId
                + ", inviterId=" + inviterId
                + ", targetId=" + targetId
                + ", expiresAt=" + expiresAt
                + '}';
    }
}
