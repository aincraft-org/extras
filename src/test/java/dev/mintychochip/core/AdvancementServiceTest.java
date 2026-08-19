package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.AdvancementResult;
import dev.mintychochip.api.CustomAdvancement;
import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.MaterialKey;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Custom-advancement grant/query/catalog lifecycle.
 *
 * <p>Drives the shipped {@link DefaultAdvancementService}: defined entries can be granted, {@code
 * has}/list read them back, repeat grants are idempotent, unknown ids are rejected, and a new
 * service on the same store still reports completions.
 */
class AdvancementServiceTest {

  @TempDir Path tempDir;

  private InProcessExtrasEventService bus;
  private final List<ExtrasEvent> events = new ArrayList<>();

  static final CustomAdvancement SHINY =
      new CustomAdvancement(
          "first-diamond",
          "Shiny!",
          "Mine your first diamond",
          MaterialKey.parse("minecraft:diamond"),
          AdvancementFrame.TASK);
  static final CustomAdvancement LEGEND =
      new CustomAdvancement(
          "extras-legend",
          "Extras Legend",
          "Complete a legendary challenge",
          MaterialKey.parse("minecraft:nether_star"),
          AdvancementFrame.CHALLENGE);

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    bus.subscribe(events::add);
  }

  private DefaultAdvancementService newService() {
    return new DefaultAdvancementService(
        new JsonAdvancementRepository(tempDir), List.of(SHINY, LEGEND), Clock.systemUTC(), bus);
  }

  @Test
  void grantIsReadableViaHasAndList() {
    DefaultAdvancementService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertTrue(service.has(playerId, "first-diamond"));
    assertFalse(service.has(playerId, "extras-legend"));
    assertEquals(Set.of("first-diamond"), service.completed(playerId));
    assertEquals(Optional.of(SHINY), service.find("first-diamond"));
    assertTrue(service.catalog().contains(SHINY));
    assertTrue(service.catalog().contains(LEGEND));
  }

  @Test
  void repeatGrantIsIdempotentAndDoesNotCreateASecondCompletion() {
    DefaultAdvancementService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertEquals(AdvancementResult.ALREADY_COMPLETED, service.grant(playerId, "first-diamond"));
    assertEquals(Set.of("first-diamond"), service.completed(playerId));
    assertTrue(service.has(playerId, "first-diamond"));
  }

  @Test
  void unknownIdIsRejectedAndDoesNotPersist() {
    DefaultAdvancementService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.UNKNOWN_ADVANCEMENT, service.grant(playerId, "no-such"));
    assertFalse(service.has(playerId, "no-such"));
    assertTrue(service.completed(playerId).isEmpty());

    DefaultAdvancementService reader = newService();
    assertFalse(reader.has(playerId, "no-such"));
    assertTrue(reader.completed(playerId).isEmpty());
  }

  @Test
  void completionSurvivesReconstructingServiceOnTheSameStore() {
    Path dir = tempDir.resolve("advancements");
    UUID playerId = UUID.randomUUID();
    List<CustomAdvancement> catalog = List.of(SHINY, LEGEND);

    DefaultAdvancementService writer =
        new DefaultAdvancementService(new JsonAdvancementRepository(dir), catalog);
    assertEquals(AdvancementResult.GRANTED, writer.grant(playerId, "  FIRST-DIAMOND  "));
    assertEquals(AdvancementResult.GRANTED, writer.grant(playerId, "extras-legend"));

    DefaultAdvancementService reader =
        new DefaultAdvancementService(new JsonAdvancementRepository(dir), catalog);
    assertTrue(reader.has(playerId, "first-diamond"));
    assertTrue(reader.has(playerId, "extras-legend"));
    assertEquals(Set.of("first-diamond", "extras-legend"), reader.completed(playerId));
  }

  @Test
  void registerAddsCatalogEntryForDownstreamGrant() {
    DefaultAdvancementService service =
        new DefaultAdvancementService(
            new JsonAdvancementRepository(tempDir), List.of(), Clock.systemUTC(), bus);
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.UNKNOWN_ADVANCEMENT, service.grant(playerId, "first-diamond"));
    service.register(SHINY);
    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertTrue(service.has(playerId, "first-diamond"));
  }

  @Test
  void firstGrantPublishesEventUnknownAndRepeatDoNot() {
    DefaultAdvancementService service = newService();
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.UNKNOWN_ADVANCEMENT, service.grant(playerId, "missing"));
    assertTrue(events.isEmpty());

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertEquals(1, events.size());
    ExtrasEvent event = events.get(0);
    assertTrue(event instanceof ExtrasEvent.AdvancementGranted, "got: " + event);
    ExtrasEvent.AdvancementGranted granted = (ExtrasEvent.AdvancementGranted) event;
    assertEquals(playerId, granted.playerId());
    assertEquals("first-diamond", granted.advancementId());

    events.clear();
    assertEquals(AdvancementResult.ALREADY_COMPLETED, service.grant(playerId, "first-diamond"));
    assertTrue(events.isEmpty());
  }
}
