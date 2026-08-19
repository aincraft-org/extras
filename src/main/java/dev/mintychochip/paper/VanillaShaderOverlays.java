package dev.mintychochip.paper;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Maps cinematic shader overlay ids onto vanilla post/core potion-effect keys.
 *
 * <p>Unknown ids stay tracked by the playback session but are not applied as client packs.
 */
public final class VanillaShaderOverlays {

  private static final Set<String> SHADER_EFFECTS =
      Set.of("darkness", "nausea", "blindness", "night_vision", "poison", "wither");

  private VanillaShaderOverlays() {}

  /**
   * Returns a {@code minecraft:<effect>} key when {@code overlayId} names a vanilla shader-like
   * status effect.
   */
  public static Optional<String> potionEffectKey(String overlayId) {
    if (overlayId == null) {
      return Optional.empty();
    }
    String id = overlayId.trim().toLowerCase(Locale.ROOT);
    if (id.startsWith("minecraft:")) {
      id = id.substring("minecraft:".length());
    }
    if (SHADER_EFFECTS.contains(id)) {
      return Optional.of("minecraft:" + id);
    }
    return Optional.empty();
  }
}
