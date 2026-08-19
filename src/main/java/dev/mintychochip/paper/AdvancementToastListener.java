package dev.mintychochip.paper;

import dev.mintychochip.api.AdvancementService;
import dev.mintychochip.api.AdvancementToastRequest;
import dev.mintychochip.api.AdvancementToastSender;
import dev.mintychochip.api.events.ExtrasEvent;
import java.util.Objects;

/**
 * After a committed first-time grant, maps the catalog display onto the toast API.
 *
 * <p>Repeat grants emit no event, so they never request another toast.
 */
public final class AdvancementToastListener {

  private final AdvancementService advancements;
  private final AdvancementToastSender toasts;

  public AdvancementToastListener(AdvancementService advancements, AdvancementToastSender toasts) {
    this.advancements = Objects.requireNonNull(advancements, "advancements");
    this.toasts = Objects.requireNonNull(toasts, "toasts");
  }

  public void onGranted(ExtrasEvent.AdvancementGranted event) {
    Objects.requireNonNull(event, "event");
    advancements
        .find(event.advancementId())
        .map(definition -> AdvancementToastRequest.from(event.playerId(), definition))
        .ifPresent(toasts::send);
  }
}
