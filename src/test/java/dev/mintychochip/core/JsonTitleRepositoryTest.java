package dev.mintychochip.core;

import dev.mintychochip.api.TitleProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence and restart behavior of the JSON title store. */
class JsonTitleRepositoryTest {

    @TempDir
    Path tempDir;

    private final UUID playerId = UUID.randomUUID();

    private JsonTitleRepository newRepository() {
        return new JsonTitleRepository(tempDir);
    }

    @Test
    void roundTripPreservesUnlockedAndEquippedTitles() {
        JsonTitleRepository repository = newRepository();
        repository.save(new TitleProfile(playerId,
                new LinkedHashSet<>(Set.of("Flamebringer", "Void-Touched")), "Flamebringer"));

        Optional<TitleProfile> reloaded = repository.findById(playerId);
        assertTrue(reloaded.isPresent());
        assertEquals(Set.of("Flamebringer", "Void-Touched"), reloaded.get().unlockedTitles());
        assertEquals("Flamebringer", reloaded.get().equippedTitle().orElseThrow());
        // playerId round-trips.
        assertEquals(playerId, reloaded.get().playerId());
        repository.close();
    }

    @Test
    void titlesWithQuotesBackslashesAndUnicodeSurvive() {
        JsonTitleRepository repository = newRepository();
        repository.save(new TitleProfile(playerId,
                new LinkedHashSet<>(Set.of("She said \"hi\" \\ yo", "Viña del Mar ☀")), "She said \"hi\" \\ yo"));

        Optional<TitleProfile> reloaded = repository.findById(playerId);
        assertEquals(Set.of("She said \"hi\" \\ yo", "Viña del Mar ☀"), reloaded.get().unlockedTitles());
        assertEquals("She said \"hi\" \\ yo", reloaded.get().equippedTitle().orElseThrow());
    }

    @Test
    void legacyFileWithoutTitleFieldsDecodesToEmptyAndRoundTrips() throws Exception {
        JsonTitleRepository repository = newRepository();
        java.nio.file.Files.writeString(repository.fileFor(playerId),
                """
                {
                  "playerId": "%s"
                }
                """.replace("%s", playerId.toString()));

        String raw = java.nio.file.Files.readString(repository.fileFor(playerId));
        assertFalse(raw.contains("unlockedTitles"));
        assertFalse(raw.contains("equippedTitle"));

        TitleProfile decoded = JsonTitleRepository.decode(raw, playerId);
        assertTrue(decoded.unlockedTitles().isEmpty());
        assertFalse(decoded.equippedTitle().isPresent());
    }

    @Test
    void equippedTitleOutsideUnlockedIsDroppedOnDecode() {
        JsonTitleRepository repository = newRepository();
        Set<String> unlocked = new LinkedHashSet<>();
        unlocked.add("A");

        TitleProfile bad = new TitleProfile(playerId, unlocked, "NotUnlocked");
        TitleProfile decoded = JsonTitleRepository.decode(JsonTitleRepository.encode(bad), playerId);

        assertEquals(Set.of("A"), decoded.unlockedTitles());
        assertFalse(decoded.equippedTitle().isPresent());
    }

    @Test
    void unknownPlayerReadsEmpty() {
        JsonTitleRepository repository = newRepository();
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
        repository.close();
    }
}
