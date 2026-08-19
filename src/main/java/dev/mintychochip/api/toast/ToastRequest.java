package dev.mintychochip.api.toast;

import dev.mintychochip.api.rewards.MaterialKey;
import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit-free toast payload: title, icon material key, and frame.
 *
 * <p>Paper maps this onto {@code io.papermc.paper.advancement.AdvancementDisplay} and {@code
 * show_toast}. Chat, title, and action bar are not substitutes.
 */
public record ToastRequest(UUID playerId, String title, String icon, ToastFrame frame) {

  public ToastRequest {
    Objects.requireNonNull(playerId, "playerId");
    title = requireText(title, "title");
    icon = MaterialKey.parse(requireText(icon, "icon")).toString();
    Objects.requireNonNull(frame, "frame");
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }
}
