package dev.mintychochip.api.toast;

import java.util.Locale;
import java.util.Objects;

/**
 * Minecraft toast frame. Matches the vanilla / Paper advancement-display vocabulary: {@code task},
 * {@code goal}, {@code challenge}.
 */
public enum ToastFrame {
  TASK,
  GOAL,
  CHALLENGE;

  /**
   * Parses a frame name ({@code task}, {@code goal}, {@code challenge}), ignoring case and
   * surrounding whitespace.
   */
  public static ToastFrame parse(String value) {
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
