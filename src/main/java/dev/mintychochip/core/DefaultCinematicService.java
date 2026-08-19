package dev.mintychochip.core;

import dev.mintychochip.api.cinematic.CameraKeyframe;
import dev.mintychochip.api.cinematic.CameraPose;
import dev.mintychochip.api.cinematic.CinematicResult;
import dev.mintychochip.api.cinematic.CinematicScene;
import dev.mintychochip.api.cinematic.CinematicService;
import dev.mintychochip.api.cinematic.OverlayCue;
import dev.mintychochip.api.cinematic.PlaybackSnapshot;
import dev.mintychochip.api.cinematic.PropCue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default Bukkit-free {@link CinematicService}: JSON-backed scene drafts plus in-memory playback
 * sessions.
 */
public final class DefaultCinematicService implements CinematicService {

  private final CinematicRepository repository;
  private final ConcurrentMap<String, CinematicDraft> drafts = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();
  private final Object mutationLock = new Object();

  public DefaultCinematicService(CinematicRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
    for (CinematicDraft draft : repository.loadAll()) {
      drafts.put(draft.name(), draft);
    }
  }

  @Override
  public CinematicResult save(CinematicScene scene) {
    Objects.requireNonNull(scene, "scene");
    synchronized (mutationLock) {
      CinematicDraft draft =
          new CinematicDraft(scene.name(), scene.keyframes(), scene.shaders(), scene.props());
      drafts.put(scene.name(), draft);
      repository.save(draft);
      return CinematicResult.SUCCESS;
    }
  }

  @Override
  public CinematicResult create(String name) {
    String normalized = CinematicScene.normalizeName(name);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    synchronized (mutationLock) {
      if (drafts.containsKey(normalized)) {
        return CinematicResult.ALREADY_EXISTS;
      }
      CinematicDraft draft = CinematicDraft.empty(normalized);
      drafts.put(normalized, draft);
      repository.save(draft);
      return CinematicResult.SUCCESS;
    }
  }

  @Override
  public CinematicResult addKeyframe(String sceneName, CameraPose pose) {
    Objects.requireNonNull(pose, "pose");
    String normalized = CinematicScene.normalizeName(sceneName);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    synchronized (mutationLock) {
      CinematicDraft draft = drafts.get(normalized);
      if (draft == null) {
        return CinematicResult.UNKNOWN_SCENE;
      }
      double time = 0.0;
      if (!draft.keyframes().isEmpty()) {
        time = draft.keyframes().getLast().timeSeconds() + 1.0;
      }
      return addKeyframeUnlocked(normalized, draft, new CameraKeyframe(time, pose));
    }
  }

  @Override
  public CinematicResult addKeyframe(String sceneName, CameraKeyframe keyframe) {
    Objects.requireNonNull(keyframe, "keyframe");
    String normalized = CinematicScene.normalizeName(sceneName);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    synchronized (mutationLock) {
      CinematicDraft draft = drafts.get(normalized);
      if (draft == null) {
        return CinematicResult.UNKNOWN_SCENE;
      }
      return addKeyframeUnlocked(normalized, draft, keyframe);
    }
  }

  private CinematicResult addKeyframeUnlocked(
      String normalized, CinematicDraft draft, CameraKeyframe keyframe) {
    for (CameraKeyframe existing : draft.keyframes()) {
      if (Double.compare(existing.timeSeconds(), keyframe.timeSeconds()) == 0) {
        return CinematicResult.INVALID_KEYFRAME;
      }
    }
    CinematicDraft next = draft.withKeyframe(keyframe);
    drafts.put(normalized, next);
    repository.save(next);
    return CinematicResult.SUCCESS;
  }

  @Override
  public CinematicResult addShader(String sceneName, OverlayCue cue) {
    Objects.requireNonNull(cue, "cue");
    return mutate(sceneName, draft -> draft.withShader(cue));
  }

  @Override
  public CinematicResult addProp(String sceneName, PropCue cue) {
    Objects.requireNonNull(cue, "cue");
    return mutate(sceneName, draft -> draft.withProp(cue));
  }

  @Override
  public Optional<CinematicScene> scene(String name) {
    String normalized = CinematicScene.normalizeName(name);
    if (normalized == null) {
      return Optional.empty();
    }
    CinematicDraft draft = drafts.get(normalized);
    return draft == null ? Optional.empty() : draft.complete();
  }

  @Override
  public Collection<CinematicScene> scenes() {
    List<CinematicScene> scenes = new ArrayList<>();
    for (CinematicDraft draft : drafts.values()) {
      draft.complete().ifPresent(scenes::add);
    }
    scenes.sort(Comparator.comparing(CinematicScene::name));
    return List.copyOf(scenes);
  }

  @Override
  public CinematicResult play(UUID playerId, String sceneName, CameraPose currentPose) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(currentPose, "currentPose");
    String normalized = CinematicScene.normalizeName(sceneName);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    synchronized (mutationLock) {
      if (sessions.containsKey(playerId)) {
        return CinematicResult.ALREADY_PLAYING;
      }
      CinematicDraft draft = drafts.get(normalized);
      if (draft == null) {
        return CinematicResult.UNKNOWN_SCENE;
      }
      Optional<CinematicScene> complete = draft.complete();
      if (complete.isEmpty()) {
        return CinematicResult.TOO_FEW_KEYFRAMES;
      }
      sessions.put(playerId, new Session(complete.get(), currentPose));
      return CinematicResult.SUCCESS;
    }
  }

  @Override
  public Optional<PlaybackSnapshot> stop(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    Session session = sessions.remove(playerId);
    if (session == null) {
      return Optional.empty();
    }
    return Optional.of(PlaybackSnapshot.restored(session.restorePose));
  }

  @Override
  public Optional<PlaybackSnapshot> samplePlayback(UUID playerId, double elapsedSeconds) {
    Objects.requireNonNull(playerId, "playerId");
    Session session = sessions.get(playerId);
    if (session == null) {
      return Optional.empty();
    }
    if (elapsedSeconds >= session.scene.durationSeconds()) {
      sessions.remove(playerId, session);
      return Optional.of(PlaybackSnapshot.restored(session.restorePose));
    }
    return Optional.of(PlaybackSnapshot.playing(session.scene.sample(elapsedSeconds)));
  }

  public void close() {
    sessions.clear();
    drafts.clear();
    repository.close();
  }

  private CinematicResult mutate(
      String sceneName, java.util.function.Function<CinematicDraft, CinematicDraft> update) {
    String normalized = CinematicScene.normalizeName(sceneName);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    synchronized (mutationLock) {
      CinematicDraft draft = drafts.get(normalized);
      if (draft == null) {
        return CinematicResult.UNKNOWN_SCENE;
      }
      CinematicDraft next = update.apply(draft);
      drafts.put(normalized, next);
      repository.save(next);
      return CinematicResult.SUCCESS;
    }
  }

  private static final class Session {
    private final CinematicScene scene;
    private final CameraPose restorePose;

    private Session(CinematicScene scene, CameraPose restorePose) {
      this.scene = scene;
      this.restorePose = restorePose;
    }
  }
}
