package dev.mintychochip.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of a mutual friendship.
 *
 * <p>The pair is stored canonically ({@code playerA} orders before
 * {@code playerB} by {@link UUID#compareTo}) so the store can enforce a
 * single row per unordered pair. Read views always expose the canonical
 * ordering; consumers must not rely on which side is which.
 */
public final class Friendship {

    private final UUID playerA;
    private final UUID playerB;
    private final Instant since;

    public Friendship(UUID playerA, UUID playerB, Instant since) {
        this.playerA = Objects.requireNonNull(playerA, "playerA");
        this.playerB = Objects.requireNonNull(playerB, "playerB");
        this.since = Objects.requireNonNull(since, "since");
    }

    public UUID playerA() {
        return playerA;
    }

    public UUID playerB() {
        return playerB;
    }

    public Instant since() {
        return since;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Friendship that)) {
            return false;
        }
        return playerA.equals(that.playerA)
                && playerB.equals(that.playerB)
                && since.equals(that.since);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerA, playerB, since);
    }

    @Override
    public String toString() {
        return "Friendship{playerA=" + playerA
                + ", playerB=" + playerB
                + ", since=" + since
                + '}';
    }
}
