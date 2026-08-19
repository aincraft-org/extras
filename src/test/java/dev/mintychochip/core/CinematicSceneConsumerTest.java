package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.cinematic.CameraKeyframe;
import dev.mintychochip.api.cinematic.CameraPose;
import dev.mintychochip.api.cinematic.CinematicResult;
import dev.mintychochip.api.cinematic.CinematicScene;
import dev.mintychochip.api.cinematic.CinematicService;
import dev.mintychochip.api.cinematic.OverlayCue;
import dev.mintychochip.api.cinematic.PropCue;
import dev.mintychochip.api.cinematic.SceneSample;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Throwaway-style consumer of the public cinematic API: load a representative scene through {@link
 * CinematicService} and sample at the endpoints and midpoint.
 */
class CinematicSceneConsumerTest {

  @TempDir Path tempDir;

  @Test
  void loadsRepresentativeSceneAndSamplesZeroMidAndDuration() {
    CinematicService service = new DefaultCinematicService(new JsonCinematicRepository(tempDir));
    CameraPose start = new CameraPose("overworld", 0, 70, 0, 90f, 0f);
    CameraPose end = new CameraPose("overworld", 40, 80, 8, 0f, -10f);
    CinematicScene defined =
        CinematicScene.load(
            "opening",
            List.of(new CameraKeyframe(0, start), new CameraKeyframe(8, end)),
            List.of(new OverlayCue("nausea", 1, 5)),
            List.of(new PropCue("lantern", 0, 8, new CameraPose("overworld", 2, 70, 2, 0f, 0f))));

    assertEquals(CinematicResult.SUCCESS, service.save(defined));
    CinematicScene loaded = service.scene("opening").orElseThrow();

    SceneSample atZero = loaded.sample(0);
    SceneSample atMid = loaded.sample(loaded.durationSeconds() / 2.0);
    SceneSample atDuration = loaded.sample(loaded.durationSeconds());

    assertEquals(start, atZero.camera());
    assertFalse(atZero.shaders().contains("nausea"));
    assertEquals(1, atZero.props().size());
    assertEquals("lantern", atZero.props().getFirst().propId());

    assertTrue(atMid.camera().x() > start.x() && atMid.camera().x() < end.x());
    assertTrue(atMid.camera().y() > start.y() && atMid.camera().y() < end.y());
    assertTrue(atMid.shaders().contains("nausea"));
    assertEquals(1, atMid.props().size());

    assertEquals(end, atDuration.camera());
    assertFalse(atDuration.shaders().contains("nausea"));
    assertEquals(1, atDuration.props().size());
  }
}
