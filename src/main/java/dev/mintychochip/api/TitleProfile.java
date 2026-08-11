package dev.mintychochip.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of a player's title state.
 *
 * <p>Titles are arbitrary strings (no fixed catalog). A player holds an
 * unordered set of unlocked title ids and may equip at most one of them at a
 * time; an equipped title is always a member of the unlocked set.
 */
public final class TitleProfile {

    private final UUID playerId;
    private final Set<String> unlockedTitles;
    private final String equippedTitle;

    public TitleProfile(UUID playerId, Set<String> unlockedTitles, String equippedTitle) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.unlockedTitles = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(unlockedTitles, "unlockedTitles")));
        this.equippedTitle = equippedTitle;
    }

    public UUID playerId() {
        return playerId;
    }

    public Set<String> unlockedTitles() {
        return unlockedTitles;
    }

    public Optional<String> equippedTitle() {
        return Optional.ofNullable(equippedTitle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TitleProfile that)) {
            return false;
        }
        return playerId.equals(that.playerId)
                && unlockedTitles.equals(that.unlockedTitles)
                && Objects.equals(equippedTitle, that.equippedTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, unlockedTitles, equippedTitle);
    }

    @Override
    public String toString() {
        return "TitleProfile{playerId=" + playerId
                + ", unlockedTitles=" + unlockedTitles
                + ", equippedTitle=" + equippedTitle
                + '}';
    }
}
