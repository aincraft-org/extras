package dev.mintychochip.api.toast;

/**
 * Toast SPI: show a Minecraft advancement toast.
 *
 * <p>Adventure has no {@code sendToast}. The Paper adapter implements this with the
 * advancement-display / {@code show_toast} path. Domains such as advancements call this after their
 * own persist succeeds.
 */
public interface ToastService {

  /** Requests a toast for {@code request}. Offline or unmapped players may be skipped. */
  void send(ToastRequest request);
}
