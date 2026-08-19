package dev.mintychochip.api;

import dev.mintychochip.api.rewards.MaterialKey;
import java.util.Locale;
import java.util.Objects;

/**
 * A catalog entry for a custom advancement.
 *
 * <p>Ids are stable keys used for grant/query. Title, description, icon, and frame are the same
 * display vocabulary Minecraft uses for custom advancements.
 */
public record CustomAdvancement(
    String id, String title, String description, MaterialKey icon, AdvancementFrame frame) {

  private static final int MAX_ID_LENGTH = 64;

  public CustomAdvancement {
    id = requireId(id);
    title = requireText(title, "title");
    description = requireText(description, "description");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(frame, "frame");
  }

  /**
   * Normalizes an advancement id: trim, lowercase, reject blank / oversized / control / whitespace.
   * Returns {@code null} when the id is unusable as a catalog key.
   */
  public static String normalizeId(String id) {
    if (id == null) {
      return null;
    }
    String trimmed = id.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty() || trimmed.length() > MAX_ID_LENGTH) {
      return null;
    }
    for (int i = 0; i < trimmed.length(); i++) {
      char character = trimmed.charAt(i);
      if (Character.isISOControl(character) || Character.isWhitespace(character)) {
        return null;
      }
    }
    return trimmed;
  }

  private static String requireId(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) {
      throw new IllegalArgumentException("id must be 1–64 non-control non-whitespace characters");
    }
    return normalized;
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
