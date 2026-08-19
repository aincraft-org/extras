package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CinematicCommandTest {

  @Test
  void parsesCameraShadersPropsPlayAndStop() {
    assertEquals(
        CinematicCommand.Action.CREATE, CinematicCommand.parseAction(new String[] {"create"}));
    assertEquals(
        CinematicCommand.Action.CAMERA, CinematicCommand.parseAction(new String[] {"camera"}));
    assertEquals(
        CinematicCommand.Action.SHADERS, CinematicCommand.parseAction(new String[] {"shaders"}));
    assertEquals(
        CinematicCommand.Action.PROPS, CinematicCommand.parseAction(new String[] {"props"}));
    assertEquals(CinematicCommand.Action.PLAY, CinematicCommand.parseAction(new String[] {"play"}));
    assertEquals(CinematicCommand.Action.STOP, CinematicCommand.parseAction(new String[] {"stop"}));
    assertEquals(CinematicCommand.Action.LIST, CinematicCommand.parseAction(new String[] {"list"}));
  }

  @Test
  void suggestionsExposeCameraShadersPropsPlayAndStop() {
    List<String> all = CinematicCommand.suggestions("");
    assertTrue(
        all.containsAll(List.of("create", "camera", "shaders", "props", "play", "stop", "list")));
    assertEquals(List.of("play", "props"), CinematicCommand.suggestions("p"));
    assertEquals(List.of("shaders", "stop"), CinematicCommand.suggestions("s"));
    assertEquals(List.of("camera"), CinematicCommand.suggestions("cam"));
  }

  @Test
  void permissionIsOperatorCinematicUse() {
    assertEquals("extras.cinematic.use", CinematicCommand.USE_PERMISSION);
  }

  @Test
  void shaderOverlayIdsMapToVanillaPotionEffectKeys() {
    assertEquals(
        java.util.Optional.of("minecraft:darkness"),
        VanillaShaderOverlays.potionEffectKey("darkness"));
    assertEquals(
        java.util.Optional.of("minecraft:nausea"), VanillaShaderOverlays.potionEffectKey("NAUSEA"));
    assertEquals(
        java.util.Optional.of("minecraft:night_vision"),
        VanillaShaderOverlays.potionEffectKey("minecraft:night_vision"));
    assertTrue(VanillaShaderOverlays.potionEffectKey("iris-pack").isEmpty());
  }
}
