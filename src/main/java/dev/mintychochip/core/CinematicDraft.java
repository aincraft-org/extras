package dev.mintychochip.core;

import dev.mintychochip.api.cinematic.CameraKeyframe;
import dev.mintychochip.api.cinematic.CinematicScene;
import dev.mintychochip.api.cinematic.OverlayCue;
import dev.mintychochip.api.cinematic.PropCue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persistable scene document that may still have fewer than two camera keyframes. */
public record CinematicDraft(
    String name, List<CameraKeyframe> keyframes, List<OverlayCue> shaders, List<PropCue> props) {

  public CinematicDraft {
    Objects.requireNonNull(name, "name");
    keyframes = List.copyOf(Objects.requireNonNull(keyframes, "keyframes"));
    shaders = List.copyOf(Objects.requireNonNull(shaders, "shaders"));
    props = List.copyOf(Objects.requireNonNull(props, "props"));
  }

  static CinematicDraft empty(String name) {
    return new CinematicDraft(name, List.of(), List.of(), List.of());
  }

  CinematicDraft withKeyframe(CameraKeyframe keyframe) {
    List<CameraKeyframe> next = new ArrayList<>(keyframes);
    next.removeIf(existing -> existing.timeSeconds() == keyframe.timeSeconds());
    next.add(keyframe);
    next.sort((a, b) -> Double.compare(a.timeSeconds(), b.timeSeconds()));
    return new CinematicDraft(name, next, shaders, props);
  }

  CinematicDraft withShader(OverlayCue cue) {
    List<OverlayCue> next = new ArrayList<>(shaders);
    next.add(cue);
    return new CinematicDraft(name, keyframes, next, props);
  }

  CinematicDraft withProp(PropCue cue) {
    List<PropCue> next = new ArrayList<>(props);
    next.add(cue);
    return new CinematicDraft(name, keyframes, shaders, next);
  }

  Optional<CinematicScene> complete() {
    if (keyframes.size() < 2) {
      return Optional.empty();
    }
    try {
      return Optional.of(CinematicScene.load(name, keyframes, shaders, props));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }
}
