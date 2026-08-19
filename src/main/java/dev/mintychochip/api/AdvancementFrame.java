package dev.mintychochip.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Minecraft advancement frame used by custom advancement displays and toasts.
 *
 * <p>Values match the vanilla / Paper display vocabulary: {@code task}, {@code goal}, {@code
 * challenge}.
 */
public enum AdvancementFrame {
  TASK,
  GOAL,
  CHALLENGE;

  /**
   * Parses a frame name ({@code task}, {@code goal}, {@code challenge}), ignoring case and
   * surrounding whitespace.
   */
  public static AdvancementFrame parse(String value) {
    Objects.requireNonNull(value, "value");
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "frame must be task, goal, or challenge: " + value, exception);
    }
  }
}
