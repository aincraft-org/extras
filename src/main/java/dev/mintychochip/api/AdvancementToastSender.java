package dev.mintychochip.api;

/**
 * Toast API used by custom advancements: send a Minecraft advancement toast for a display payload.
 *
 * <p>The Paper adapter implements this with the advancement-display / {@code show_toast} path.
 */
public interface AdvancementToastSender {

  /** Requests a toast for {@code request}. Offline or unmapped players may be skipped. */
  void send(AdvancementToastRequest request);
}
