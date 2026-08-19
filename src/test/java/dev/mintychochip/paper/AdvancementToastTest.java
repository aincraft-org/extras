package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.AdvancementResult;
import dev.mintychochip.api.AdvancementToastRequest;
import dev.mintychochip.api.CustomAdvancement;
import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.MaterialKey;
import dev.mintychochip.core.DefaultAdvancementService;
import dev.mintychochip.core.InProcessExtrasEventService;
import dev.mintychochip.core.JsonAdvancementRepository;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the shipped Paper toast path: first-time grant → listener → {@link
 * PaperAdvancementToastSender} → {@link PaperAdvancementToastDisplay}.
 */
class AdvancementToastTest {

  @TempDir Path tempDir;

  private InProcessExtrasEventService bus;
  private DefaultAdvancementService service;
  private final List<PaperAdvancementToastDisplay> sent = new ArrayList<>();

  private static final CustomAdvancement SHINY =
      new CustomAdvancement(
          "first-diamond",
          "Shiny!",
          "Mine your first diamond",
          MaterialKey.parse("minecraft:diamond"),
          AdvancementFrame.TASK);
  private static final CustomAdvancement PARTY =
      new CustomAdvancement(
          "party-maker",
          "Let's Party",
          "Create or join a party",
          MaterialKey.parse("minecraft:cake"),
          AdvancementFrame.GOAL);
  private static final CustomAdvancement LEGEND =
      new CustomAdvancement(
          "extras-legend",
          "Extras Legend",
          "Complete a legendary challenge",
          MaterialKey.parse("minecraft:nether_star"),
          AdvancementFrame.CHALLENGE);

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    service =
        new DefaultAdvancementService(
            new JsonAdvancementRepository(tempDir),
            List.of(SHINY, PARTY, LEGEND),
            Clock.systemUTC(),
            bus);
    PaperAdvancementToastSender sender = new PaperAdvancementToastSender(sent::add);
    AdvancementToastListener listener = new AdvancementToastListener(service, sender);
    bus.subscribe(ExtrasEvent.AdvancementGranted.class, listener::onGranted);
  }

  @Test
  void firstTimeGrantRequestsToastMatchingCatalogDisplay() {
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertEquals(1, sent.size());
    PaperAdvancementToastDisplay toast = sent.get(0);
    assertEquals(playerId, toast.playerId());
    assertEquals("Shiny!", toast.title());
    assertEquals("minecraft:diamond", toast.icon());
    assertEquals(AdvancementDisplay.Frame.TASK, toast.frame());
    assertTrue(toast.showToast());
    assertTrue(toast.advancementJson().contains("\"show_toast\": true"));
  }

  @Test
  void repeatGrantDoesNotRequestAnotherToast() {
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertEquals(AdvancementResult.ALREADY_COMPLETED, service.grant(playerId, "first-diamond"));
    assertEquals(1, sent.size());
  }

  @Test
  void framesMapToPaperAdvancementDisplayFrames() {
    UUID playerId = UUID.randomUUID();

    assertEquals(
        AdvancementDisplay.Frame.TASK,
        PaperAdvancementToastDisplay.from(AdvancementToastRequest.from(playerId, SHINY)).frame());
    assertEquals(
        AdvancementDisplay.Frame.GOAL,
        PaperAdvancementToastDisplay.from(AdvancementToastRequest.from(playerId, PARTY)).frame());
    assertEquals(
        AdvancementDisplay.Frame.CHALLENGE,
        PaperAdvancementToastDisplay.from(AdvancementToastRequest.from(playerId, LEGEND)).frame());

    service.grant(playerId, "party-maker");
    service.grant(playerId, "extras-legend");
    assertEquals(AdvancementDisplay.Frame.GOAL, sent.get(0).frame());
    assertEquals(AdvancementDisplay.Frame.CHALLENGE, sent.get(1).frame());
    assertEquals("minecraft:cake", sent.get(0).icon());
    assertEquals("Let's Party", sent.get(0).title());
    assertEquals("minecraft:nether_star", sent.get(1).icon());
    assertEquals("Extras Legend", sent.get(1).title());
  }
}
