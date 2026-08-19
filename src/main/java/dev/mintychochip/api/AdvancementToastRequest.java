package dev.mintychochip.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit-free toast payload for a custom advancement: title, icon material key, and frame.
 *
 * <p>Paper maps this onto {@code io.papermc.paper.advancement.AdvancementDisplay} and {@code
 * show_toast}. Chat, title, and action bar are not substitutes.
 */
public record AdvancementToastRequest(
    UUID playerId, String title, String icon, AdvancementFrame frame) {

  public AdvancementToastRequest {
    Objects.requireNonNull(playerId, "playerId");
    title = requireText(title, "title");
    icon = requireText(icon, "icon");
    Objects.requireNonNull(frame, "frame");
  }

  /** Builds the toast request from a catalog entry after a successful first-time grant. */
  public static AdvancementToastRequest from(UUID playerId, CustomAdvancement advancement) {
    Objects.requireNonNull(advancement, "advancement");
    return new AdvancementToastRequest(
        playerId, advancement.title(), advancement.icon().toString(), advancement.frame());
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
