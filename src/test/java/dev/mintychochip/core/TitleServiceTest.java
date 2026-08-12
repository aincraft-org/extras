package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.TitleResult;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Title grant/revoke/equip/unequip lifecycle and validation rules.
 *
 * <p>Title state is created lazily on first successful mutation: players with no stored state read
 * as empty and accept grants.
 */
class TitleServiceTest {

  @TempDir Path tempDir;

  private DefaultTitleService newService() {
    return new DefaultTitleService(new JsonTitleRepository(tempDir));
  }

  // --------------------------------------------------------------- lifecycle

  @Test
  void grantEquipUnequipRevokeLifecycle() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(TitleResult.SUCCESS, service.grantTitle(playerId, "Flamebringer"));
    assertEquals(TitleResult.SUCCESS, service.grantTitle(playerId, "Void-Touched"));
    assertEquals(Set.of("Flamebringer", "Void-Touched"), service.unlockedTitles(playerId));
    assertFalse(service.equippedTitle(playerId).isPresent());

    assertEquals(TitleResult.SUCCESS, service.equipTitle(playerId, "Flamebringer"));
    assertEquals(Optional.of("Flamebringer"), service.equippedTitle(playerId));

    assertEquals(TitleResult.SUCCESS, service.unequipTitle(playerId));
    assertFalse(service.equippedTitle(playerId).isPresent());

    assertEquals(TitleResult.SUCCESS, service.revokeTitle(playerId, "Flamebringer"));
    assertEquals(Set.of("Void-Touched"), service.unlockedTitles(playerId));

    // Revoking the equipped title unequips it.
    assertEquals(TitleResult.SUCCESS, service.equipTitle(playerId, "Void-Touched"));
    assertEquals(TitleResult.SUCCESS, service.revokeTitle(playerId, "Void-Touched"));
    assertTrue(service.unlockedTitles(playerId).isEmpty());
    assertFalse(service.equippedTitle(playerId).isPresent());
  }

  @Test
  void equipRequiresUnlock() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(TitleResult.NOT_UNLOCKED, service.equipTitle(playerId, "Unowned Title"));
    assertFalse(service.equippedTitle(playerId).isPresent());
  }

  @Test
  void grantDuplicateIsRejected() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(TitleResult.SUCCESS, service.grantTitle(playerId, "Solo"));
    assertEquals(TitleResult.ALREADY_UNLOCKED, service.grantTitle(playerId, "Solo"));
  }

  @Test
  void revokeUnownedIsRejected() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(TitleResult.NOT_UNLOCKED, service.revokeTitle(playerId, "Absent"));
  }

  @Test
  void invalidTitlesRejectedAndTrimmed() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(TitleResult.INVALID_TITLE, service.grantTitle(playerId, ""));
    assertEquals(TitleResult.INVALID_TITLE, service.grantTitle(playerId, "   "));
    assertEquals(TitleResult.INVALID_TITLE, service.grantTitle(playerId, "x".repeat(65)));
    assertEquals(TitleResult.INVALID_TITLE, service.grantTitle(playerId, "bad\u0007title"));

    // Trimmed id is stored/compared.
    assertEquals(TitleResult.SUCCESS, service.grantTitle(playerId, "  King of the North  "));
    assertTrue(service.unlockedTitles(playerId).contains("King of the North"));
    // Unequipped title is valid for equip.
    assertEquals(TitleResult.SUCCESS, service.equipTitle(playerId, "  King of the North  "));
    assertEquals(Optional.of("King of the North"), service.equippedTitle(playerId));
  }

  @Test
  void unknownPlayerReadsAsEmptyAndAcceptsGrants() {
    DefaultTitleService service = newService();
    UUID stranger = UUID.randomUUID();

    assertEquals(TitleResult.SUCCESS, service.grantTitle(stranger, "Ghost"));
    assertEquals(Set.of("Ghost"), service.unlockedTitles(stranger));
    assertEquals(TitleResult.SUCCESS, service.equipTitle(stranger, "Ghost"));
    assertEquals(Optional.of("Ghost"), service.equippedTitle(stranger));
  }

  @Test
  void equippedAndUnlockedSurviveEquipSwap() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    service.grantTitle(playerId, "A");
    service.grantTitle(playerId, "B");
    service.equipTitle(playerId, "A");
    service.equipTitle(playerId, "B");

    assertEquals(Optional.of("B"), service.equippedTitle(playerId));
    assertEquals(Set.of("A", "B"), service.unlockedTitles(playerId));
  }

  // -------------------------------------------------------------- persistence

  @Test
  void stateSurvivesServiceRestart() {
    Path dir = tempDir.resolve("titles");
    UUID playerId = UUID.randomUUID();

    DefaultTitleService writer = new DefaultTitleService(new JsonTitleRepository(dir));
    writer.grantTitle(playerId, "Flamebringer");
    writer.grantTitle(playerId, "Void-Touched");
    writer.equipTitle(playerId, "Void-Touched");

    DefaultTitleService reader = new DefaultTitleService(new JsonTitleRepository(dir));
    assertEquals(Set.of("Flamebringer", "Void-Touched"), reader.unlockedTitles(playerId));
    assertEquals(Optional.of("Void-Touched"), reader.equippedTitle(playerId));
  }

  @Test
  void titlesWithQuotesBackslashesAndUnicodeSurvive() {
    DefaultTitleService service = newService();
    UUID playerId = UUID.randomUUID();

    service.grantTitle(playerId, "She said \"hi\" \\ yo");
    service.grantTitle(playerId, "Viña del Mar ☀");
    service.equipTitle(playerId, "She said \"hi\" \\ yo");

    DefaultTitleService reader = new DefaultTitleService(new JsonTitleRepository(tempDir));
    assertEquals(
        Set.of("She said \"hi\" \\ yo", "Viña del Mar ☀"), reader.unlockedTitles(playerId));
    assertEquals(Optional.of("She said \"hi\" \\ yo"), reader.equippedTitle(playerId));
  }
}
