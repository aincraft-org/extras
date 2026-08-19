package dev.mintychochip.paper;

import dev.mintychochip.api.AdvancementFrame;
import dev.mintychochip.api.AdvancementToastRequest;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper toast payload: AdvancementDisplay frame types plus {@code show_toast}.
 *
 * <p>{@link #from(AdvancementToastRequest)} is the shipped mapper the adapter sends. Tests assert
 * this record, not a reimplementation.
 */
public record PaperAdvancementToastDisplay(
    UUID playerId, String title, String icon, AdvancementDisplay.Frame frame, boolean showToast) {

  public PaperAdvancementToastDisplay {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(frame, "frame");
  }

  /** Maps a Bukkit-free toast request onto Paper's advancement-display / toast path. */
  public static PaperAdvancementToastDisplay from(AdvancementToastRequest request) {
    Objects.requireNonNull(request, "request");
    return new PaperAdvancementToastDisplay(
        request.playerId(), request.title(), request.icon(), toPaperFrame(request.frame()), true);
  }

  static AdvancementDisplay.Frame toPaperFrame(AdvancementFrame frame) {
    return switch (frame) {
      case TASK -> AdvancementDisplay.Frame.TASK;
      case GOAL -> AdvancementDisplay.Frame.GOAL;
      case CHALLENGE -> AdvancementDisplay.Frame.CHALLENGE;
    };
  }

  /**
   * Temporary advancement JSON used by {@link PaperAdvancementToastSender}. {@code show_toast} is
   * true; chat announce is off so the toast is not substituted by a chat line.
   */
  public String advancementJson() {
    String frameName = frame.name().toLowerCase(Locale.ROOT);
    return "{\"criteria\":{\"impossible\":{\"trigger\":\"minecraft:impossible\"}},"
        + "\"display\":{"
        + "\"icon\":{\"id\":\""
        + escapeJson(icon)
        + "\"},"
        + "\"title\":{\"text\":\""
        + escapeJson(title)
        + "\"},"
        + "\"description\":{\"text\":\"\"},"
        + "\"frame\":\""
        + frameName
        + "\","
        + "\"show_toast\": true,"
        + "\"announce_to_chat\": false,"
        + "\"hidden\": true"
        + "}}";
  }

  private static String escapeJson(String value) {
    StringBuilder sb = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
