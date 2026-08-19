package dev.mintychochip.api.cinematic;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public surface for named cinematic scenes: camera paths, shader overlay cues, prop cues, and
 * per-player playback sessions.
 *
 * <p>Types are Bukkit-free. Paper maps {@link CameraPose} onto a player location, overlay ids onto
 * vanilla post/core effects, and prop cues onto display entities.
 */
public interface CinematicService {

  /** Persists a complete scene (two or more keyframes). Replaces any draft of the same name. */
  CinematicResult save(CinematicScene scene);

  /** Creates an empty named draft so operators can add camera keyframes incrementally. */
  CinematicResult create(String name);

  /** Appends or inserts a camera keyframe on a draft or complete scene. */
  CinematicResult addKeyframe(String sceneName, CameraKeyframe keyframe);

  /**
   * Adds a camera keyframe at the next whole second after the last keyframe (or {@code 0} when the
   * scene has none).
   */
  CinematicResult addKeyframe(String sceneName, CameraPose pose);

  /** Adds a timed shader overlay cue. */
  CinematicResult addShader(String sceneName, OverlayCue cue);

  /** Adds a timed prop cue. */
  CinematicResult addProp(String sceneName, PropCue cue);

  /** The complete scene for {@code name}, if it has at least two keyframes. */
  Optional<CinematicScene> scene(String name);

  /** Complete scenes in name order. */
  Collection<CinematicScene> scenes();

  /**
   * Starts playback of {@code sceneName} for {@code playerId}, recording {@code currentPose} to
   * restore on stop or completion. Rejects a second play while one is active.
   */
  CinematicResult play(UUID playerId, String sceneName, CameraPose currentPose);

  /**
   * Stops playback for {@code playerId} and returns the restored pre-play pose with no overlays or
   * props. Empty when the player is not playing.
   */
  Optional<PlaybackSnapshot> stop(UUID playerId);

  /**
   * Samples the active session at elapsed seconds. At or after the scene duration the session
   * completes and the restored pre-play pose is returned. Empty when the player is not playing.
   */
  Optional<PlaybackSnapshot> samplePlayback(UUID playerId, double elapsedSeconds);
}
