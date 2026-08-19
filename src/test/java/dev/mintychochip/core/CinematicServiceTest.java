package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.cinematic.CameraKeyframe;
import dev.mintychochip.api.cinematic.CameraPose;
import dev.mintychochip.api.cinematic.CinematicResult;
import dev.mintychochip.api.cinematic.CinematicScene;
import dev.mintychochip.api.cinematic.OverlayCue;
import dev.mintychochip.api.cinematic.PlaybackSnapshot;
import dev.mintychochip.api.cinematic.PropCue;
import dev.mintychochip.api.cinematic.SceneSample;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cinematic scene load rules, camera interpolation properties, overlay/prop windows, and play/stop
 * restore.
 */
class CinematicServiceTest {

  @TempDir Path tempDir;

  private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000021");

  private DefaultCinematicService newService() {
    return new DefaultCinematicService(new JsonCinematicRepository(tempDir));
  }

  private static CameraPose pose(String world, double x, double y, double z) {
    return new CameraPose(world, x, y, z, 0f, 0f);
  }

  private static CinematicScene twoPointScene() {
    CameraPose start = pose("world", 0, 64, 0);
    CameraPose end = pose("world", 10, 64, 20);
    return CinematicScene.load(
        "intro",
        List.of(new CameraKeyframe(0, start), new CameraKeyframe(10, end)),
        List.of(new OverlayCue("darkness", 2, 6)),
        List.of(new PropCue("oak_sign", 3, 8, pose("world", 1, 64, 1))));
  }

  @Test
  void oneKeyframeSceneIsRejected() {
    CameraKeyframe only = new CameraKeyframe(0, pose("world", 0, 64, 0));
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> CinematicScene.load("solo", List.of(only), List.of(), List.of()));
    assertTrue(thrown.getMessage().toLowerCase(java.util.Locale.ROOT).contains("keyframe"));

    DefaultCinematicService service = newService();
    assertEquals(CinematicResult.SUCCESS, service.create("solo"));
    assertEquals(CinematicResult.SUCCESS, service.addKeyframe("solo", only));
    assertEquals(CinematicResult.TOO_FEW_KEYFRAMES, service.play(playerId, "solo", only.pose()));
  }

  @Test
  void sampleEndpointsMatchKeyframesAndMidpointLiesOnThePath() {
    CinematicScene scene = twoPointScene();
    CameraPose first = scene.keyframes().getFirst().pose();
    CameraPose last = scene.keyframes().getLast().pose();

    SceneSample atStart = scene.sample(0);
    SceneSample atEnd = scene.sample(scene.durationSeconds());
    SceneSample atMid = scene.sample(scene.durationSeconds() / 2.0);

    assertEquals(first.worldIdentity(), atStart.camera().worldIdentity());
    assertEquals(first.x(), atStart.camera().x(), 1e-9);
    assertEquals(first.y(), atStart.camera().y(), 1e-9);
    assertEquals(first.z(), atStart.camera().z(), 1e-9);
    assertEquals(first.yaw(), atStart.camera().yaw(), 1e-6f);
    assertEquals(first.pitch(), atStart.camera().pitch(), 1e-6f);

    assertEquals(last.worldIdentity(), atEnd.camera().worldIdentity());
    assertEquals(last.x(), atEnd.camera().x(), 1e-9);
    assertEquals(last.y(), atEnd.camera().y(), 1e-9);
    assertEquals(last.z(), atEnd.camera().z(), 1e-9);

    assertEquals(first.worldIdentity(), atMid.camera().worldIdentity());
    assertTrue(atMid.camera().x() > first.x() && atMid.camera().x() < last.x());
    assertTrue(atMid.camera().z() > first.z() && atMid.camera().z() < last.z());
    assertNotEquals(last.x(), atMid.camera().x());
    assertNotEquals(last.z(), atMid.camera().z());
    assertEquals(64.0, atMid.camera().y(), 1e-9);
  }

  @Test
  void overlayAndPropCuesAreActiveOnlyInsideTheirWindows() {
    CinematicScene scene = twoPointScene();

    SceneSample before = scene.sample(1.0);
    assertFalse(before.shaders().contains("darkness"));
    assertTrue(before.props().isEmpty());

    SceneSample overlayOnly = scene.sample(2.5);
    assertTrue(overlayOnly.shaders().contains("darkness"));
    assertTrue(overlayOnly.props().isEmpty());

    SceneSample both = scene.sample(4.0);
    assertTrue(both.shaders().contains("darkness"));
    assertEquals(1, both.props().size());
    assertEquals("oak_sign", both.props().getFirst().propId());

    SceneSample propOnly = scene.sample(7.0);
    assertFalse(propOnly.shaders().contains("darkness"));
    assertEquals(1, propOnly.props().size());

    SceneSample after = scene.sample(9.0);
    assertTrue(after.shaders().isEmpty());
    assertTrue(after.props().isEmpty());

    assertTrue(scene.sample(2.0).shaders().contains("darkness"));
    assertTrue(scene.sample(6.0).shaders().contains("darkness"));
    assertEquals(1, scene.sample(3.0).props().size());
    assertEquals(1, scene.sample(8.0).props().size());
  }

  @Test
  void playThenStopRestoresPrePlayPoseAndClearsOverlaysAndProps() {
    DefaultCinematicService service = newService();
    CinematicScene scene = twoPointScene();
    assertEquals(CinematicResult.SUCCESS, service.save(scene));

    CameraPose original = pose("world", 100, 70, 100);
    assertEquals(CinematicResult.SUCCESS, service.play(playerId, "intro", original));

    PlaybackSnapshot mid = service.samplePlayback(playerId, 4.0).orElseThrow();
    assertTrue(mid.playing());
    assertTrue(mid.shaders().contains("darkness"));
    assertFalse(mid.props().isEmpty());
    assertTrue(mid.pose().x() > 0 && mid.pose().x() < 10);

    PlaybackSnapshot stopped = service.stop(playerId).orElseThrow();
    assertFalse(stopped.playing());
    assertEquals(original, stopped.pose());
    assertTrue(stopped.shaders().isEmpty());
    assertTrue(stopped.props().isEmpty());
    assertTrue(service.samplePlayback(playerId, 4.0).isEmpty());
  }

  @Test
  void secondPlayForTheSamePlayerIsRejectedUntilStop() {
    DefaultCinematicService service = newService();
    service.save(twoPointScene());
    CameraPose original = pose("world", 1, 64, 1);
    assertEquals(CinematicResult.SUCCESS, service.play(playerId, "intro", original));
    assertEquals(
        CinematicResult.ALREADY_PLAYING, service.play(playerId, "intro", pose("world", 2, 64, 2)));
    assertTrue(service.stop(playerId).isPresent());
    assertEquals(CinematicResult.SUCCESS, service.play(playerId, "intro", original));
  }

  @Test
  void playbackCompletesAtDurationAndRestoresPose() {
    DefaultCinematicService service = newService();
    service.save(twoPointScene());
    CameraPose original = pose("world", 50, 80, 50);
    assertEquals(CinematicResult.SUCCESS, service.play(playerId, "intro", original));

    PlaybackSnapshot done = service.samplePlayback(playerId, 10.0).orElseThrow();
    assertFalse(done.playing());
    assertEquals(original, done.pose());
    assertTrue(done.shaders().isEmpty());
    assertTrue(done.props().isEmpty());
    assertTrue(service.samplePlayback(playerId, 0).isEmpty());
  }

  @Test
  void savedSceneReloadsFromTheRepository() {
    CinematicScene scene = twoPointScene();
    DefaultCinematicService writer = newService();
    assertEquals(CinematicResult.SUCCESS, writer.save(scene));

    DefaultCinematicService reader =
        new DefaultCinematicService(new JsonCinematicRepository(tempDir));
    CinematicScene loaded = reader.scene("intro").orElseThrow();
    assertEquals(scene.durationSeconds(), loaded.durationSeconds(), 1e-9);
    assertEquals(2, loaded.keyframes().size());
    assertEquals(1, loaded.shaders().size());
    assertEquals(1, loaded.props().size());
    assertEquals(scene.keyframes().getFirst().pose(), loaded.keyframes().getFirst().pose());
    assertEquals(scene.keyframes().getLast().pose(), loaded.keyframes().getLast().pose());
    SceneSample originalMid = scene.sample(5);
    SceneSample loadedMid = loaded.sample(5);
    assertEquals(originalMid.camera().x(), loadedMid.camera().x(), 1e-9);
    assertEquals(originalMid.camera().z(), loadedMid.camera().z(), 1e-9);
    assertEquals(originalMid.shaders(), loadedMid.shaders());
    assertEquals(originalMid.props(), loadedMid.props());
  }
}
