package dev.mintychochip.api.cinematic;

import java.util.List;
import java.util.Objects;

/** Camera pose plus the shader overlay ids and prop cues whose windows contain the sample time. */
public record SceneSample(CameraPose camera, List<String> shaders, List<PropCue> props) {

  public SceneSample {
    Objects.requireNonNull(camera, "camera");
    shaders = List.copyOf(Objects.requireNonNull(shaders, "shaders"));
    props = List.copyOf(Objects.requireNonNull(props, "props"));
  }
}
