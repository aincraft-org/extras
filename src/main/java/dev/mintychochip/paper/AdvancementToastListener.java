package dev.mintychochip.paper;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.AdvancementService;
import dev.mintychochip.api.CustomAdvancement;
import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.toast.ToastFrame;
import dev.mintychochip.api.toast.ToastRequest;
import dev.mintychochip.api.toast.ToastService;
import java.util.Objects;
import java.util.UUID;

/**
 * After a committed first-time grant, maps the catalog display onto {@link ToastService}.
 *
 * <p>Repeat grants emit no event, so they never request another toast.
 */
public final class AdvancementToastListener {

  private final AdvancementService advancements;
  private final ToastService toasts;

  public AdvancementToastListener(AdvancementService advancements, ToastService toasts) {
    this.advancements = Objects.requireNonNull(advancements, "advancements");
    this.toasts = Objects.requireNonNull(toasts, "toasts");
  }

  public void onGranted(ExtrasEvent.AdvancementGranted event) {
    Objects.requireNonNull(event, "event");
    advancements
        .find(event.advancementId())
        .map(definition -> request(event.playerId(), definition))
        .ifPresent(toasts::send);
  }

  static ToastRequest request(UUID playerId, CustomAdvancement advancement) {
    return new ToastRequest(
        playerId,
        advancement.title(),
        advancement.icon().toString(),
        toastFrame(advancement.frame()));
  }

  static ToastFrame toastFrame(AdvancementFrame frame) {
    return switch (frame) {
      case TASK -> ToastFrame.TASK;
      case GOAL -> ToastFrame.GOAL;
      case CHALLENGE -> ToastFrame.CHALLENGE;
    };
  }
}
