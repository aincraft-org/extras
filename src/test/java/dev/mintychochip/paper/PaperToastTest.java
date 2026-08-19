package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.toast.ToastFrame;
import dev.mintychochip.api.toast.ToastRequest;
import dev.mintychochip.api.toast.ToastService;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Drives the shipped Paper toast SPI: {@link PaperToastSender} → {@link PaperToastDisplay}.
 *
 * <p>Independent of advancements: any caller can send a title, icon, and frame.
 */
class PaperToastTest {

  @Test
  void sendMapsTitleIconFrameAndShowToast() {
    List<PaperToastDisplay> sent = new ArrayList<>();
    ToastService toasts = new PaperToastSender(sent::add);
    UUID playerId = UUID.randomUUID();

    toasts.send(new ToastRequest(playerId, "Shiny!", "minecraft:diamond", ToastFrame.TASK));

    assertEquals(1, sent.size());
    PaperToastDisplay toast = sent.get(0);
    assertEquals(playerId, toast.playerId());
    assertEquals("Shiny!", toast.title());
    assertEquals("minecraft:diamond", toast.icon());
    assertEquals(AdvancementDisplay.Frame.TASK, toast.frame());
    assertTrue(toast.showToast());
    assertTrue(toast.advancementJson().contains("\"show_toast\": true"));
    assertTrue(toast.advancementJson().contains("\"announce_to_chat\": false"));
  }

  @Test
  void framesMapToPaperAdvancementDisplayFrames() {
    UUID playerId = UUID.randomUUID();
    assertEquals(
        AdvancementDisplay.Frame.TASK,
        PaperToastDisplay.from(
                new ToastRequest(playerId, "Task", "minecraft:stone", ToastFrame.TASK))
            .frame());
    assertEquals(
        AdvancementDisplay.Frame.GOAL,
        PaperToastDisplay.from(
                new ToastRequest(playerId, "Goal", "minecraft:cake", ToastFrame.GOAL))
            .frame());
    assertEquals(
        AdvancementDisplay.Frame.CHALLENGE,
        PaperToastDisplay.from(
                new ToastRequest(
                    playerId, "Challenge", "minecraft:nether_star", ToastFrame.CHALLENGE))
            .frame());
  }
}
