package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.AdvancementResult;
import dev.mintychochip.api.CustomAdvancement;
import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.MaterialKey;
import dev.mintychochip.api.toast.ToastFrame;
import dev.mintychochip.api.toast.ToastRequest;
import dev.mintychochip.api.toast.ToastService;
import dev.mintychochip.core.DefaultAdvancementService;
import dev.mintychochip.core.InProcessExtrasEventService;
import dev.mintychochip.core.JsonAdvancementRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives first-time grant → {@link AdvancementToastListener} → {@link ToastService}.
 *
 * <p>The Paper packet path is covered by {@link PaperToastTest}; this test asserts advancements
 * consume the standalone toast SPI.
 */
class AdvancementToastTest {

  @TempDir Path tempDir;

  private InProcessExtrasEventService bus;
  private DefaultAdvancementService service;
  private final List<ToastRequest> sent = new ArrayList<>();

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
    ToastService toasts = sent::add;
    AdvancementToastListener listener = new AdvancementToastListener(service, toasts);
    bus.subscribe(ExtrasEvent.AdvancementGranted.class, listener::onGranted);
  }

  @Test
  void firstTimeGrantSendsToastMatchingCatalogDisplay() {
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertEquals(1, sent.size());
    ToastRequest toast = sent.get(0);
    assertEquals(playerId, toast.playerId());
    assertEquals("Shiny!", toast.title());
    assertEquals("minecraft:diamond", toast.icon());
    assertEquals(ToastFrame.TASK, toast.frame());
  }

  @Test
  void repeatGrantDoesNotRequestAnotherToast() {
    UUID playerId = UUID.randomUUID();

    assertEquals(AdvancementResult.GRANTED, service.grant(playerId, "first-diamond"));
    assertEquals(AdvancementResult.ALREADY_COMPLETED, service.grant(playerId, "first-diamond"));
    assertEquals(1, sent.size());
  }

  @Test
  void framesMapOntoToastSpiFrames() {
    UUID playerId = UUID.randomUUID();

    assertEquals(ToastFrame.TASK, AdvancementToastListener.toastFrame(AdvancementFrame.TASK));
    assertEquals(ToastFrame.GOAL, AdvancementToastListener.toastFrame(AdvancementFrame.GOAL));
    assertEquals(
        ToastFrame.CHALLENGE, AdvancementToastListener.toastFrame(AdvancementFrame.CHALLENGE));

    service.grant(playerId, "party-maker");
    service.grant(playerId, "extras-legend");
    assertEquals(ToastFrame.GOAL, sent.get(0).frame());
    assertEquals(ToastFrame.CHALLENGE, sent.get(1).frame());
    assertEquals("minecraft:cake", sent.get(0).icon());
    assertEquals("Let's Party", sent.get(0).title());
    assertEquals("minecraft:nether_star", sent.get(1).icon());
    assertEquals("Extras Legend", sent.get(1).title());
  }
}
