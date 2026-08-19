package dev.mintychochip.core;

import dev.mintychochip.api.cinematic.CameraKeyframe;
import dev.mintychochip.api.cinematic.CameraPose;
import dev.mintychochip.api.cinematic.OverlayCue;
import dev.mintychochip.api.cinematic.PropCue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-backed {@link CinematicRepository}: one JSON document per scene under a data directory.
 *
 * <p>Uses a hand-rolled codec (no third-party JSON library). A missing file is unknown; a corrupted
 * document degrades to empty rather than failing the load.
 */
public final class JsonCinematicRepository implements CinematicRepository {

  private static final Pattern STRING_FIELD =
      Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
  private static final Pattern NUMBER_FIELD =
      Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");

  private final Path dataDirectory;

  public JsonCinematicRepository(Path dataDirectory) {
    this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    try {
      Files.createDirectories(dataDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to create cinematic data directory: " + dataDirectory, e);
    }
  }

  @Override
  public void save(CinematicDraft draft) {
    Objects.requireNonNull(draft, "draft");
    Path file = fileFor(draft.name());
    try {
      Files.createDirectories(dataDirectory);
      Files.writeString(file, encode(draft), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save cinematic scene " + draft.name(), e);
    }
  }

  @Override
  public Optional<CinematicDraft> find(String name) {
    Path file = fileFor(name);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(decode(Files.readString(file, StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read cinematic scene " + name, e);
    }
  }

  @Override
  public Collection<CinematicDraft> loadAll() {
    List<CinematicDraft> drafts = new ArrayList<>();
    if (!Files.isDirectory(dataDirectory)) {
      return List.of();
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory, "*.json")) {
      for (Path file : stream) {
        readDraft(file).ifPresent(drafts::add);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list cinematic scenes", e);
    }
    return List.copyOf(drafts);
  }

  @Override
  public void close() {
    // File-backed store holds no open resources.
  }

  Path fileFor(String name) {
    return dataDirectory.resolve(name + ".json");
  }

  private static Optional<CinematicDraft> readDraft(Path file) {
    try {
      return Optional.ofNullable(decode(Files.readString(file, StandardCharsets.UTF_8)));
    } catch (IOException failedRead) {
      return Optional.empty();
    }
  }

  static String encode(CinematicDraft draft) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\n");
    sb.append("  \"name\": ").append(encodeString(draft.name())).append(",\n");
    sb.append("  \"keyframes\": [\n");
    appendJoined(sb, draft.keyframes(), JsonCinematicRepository::encodeKeyframe);
    sb.append("  ],\n");
    sb.append("  \"shaders\": [\n");
    appendJoined(sb, draft.shaders(), JsonCinematicRepository::encodeShader);
    sb.append("  ],\n");
    sb.append("  \"props\": [\n");
    appendJoined(sb, draft.props(), JsonCinematicRepository::encodeProp);
    sb.append("  ]\n");
    sb.append("}\n");
    return sb.toString();
  }

  static CinematicDraft decode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    String name = stringField(json, "name");
    if (name == null || name.isBlank()) {
      return null;
    }
    List<CameraKeyframe> keyframes = new ArrayList<>();
    for (String body : objectArrayBodies(json, "keyframes")) {
      CameraKeyframe keyframe = decodeKeyframe(body);
      if (keyframe != null) {
        keyframes.add(keyframe);
      }
    }
    List<OverlayCue> shaders = new ArrayList<>();
    for (String body : objectArrayBodies(json, "shaders")) {
      OverlayCue cue = decodeShader(body);
      if (cue != null) {
        shaders.add(cue);
      }
    }
    List<PropCue> props = new ArrayList<>();
    for (String body : objectArrayBodies(json, "props")) {
      PropCue cue = decodeProp(body);
      if (cue != null) {
        props.add(cue);
      }
    }
    return new CinematicDraft(name, keyframes, shaders, props);
  }

  private static String encodeKeyframe(CameraKeyframe keyframe) {
    return "    {\"time\": " + keyframe.timeSeconds() + ", " + poseFields(keyframe.pose()) + "}";
  }

  private static String encodeShader(OverlayCue cue) {
    return "    {\"id\": "
        + encodeString(cue.overlayId())
        + ", \"start\": "
        + cue.startSeconds()
        + ", \"end\": "
        + cue.endSeconds()
        + "}";
  }

  private static String encodeProp(PropCue cue) {
    return "    {\"id\": "
        + encodeString(cue.propId())
        + ", \"start\": "
        + cue.startSeconds()
        + ", \"end\": "
        + cue.endSeconds()
        + ", "
        + poseFields(cue.pose())
        + "}";
  }

  private static String poseFields(CameraPose pose) {
    return "\"world\": "
        + encodeString(pose.worldIdentity())
        + ", \"x\": "
        + pose.x()
        + ", \"y\": "
        + pose.y()
        + ", \"z\": "
        + pose.z()
        + ", \"yaw\": "
        + pose.yaw()
        + ", \"pitch\": "
        + pose.pitch();
  }

  private static CameraKeyframe decodeKeyframe(String body) {
    Double time = numberField(body, "time");
    CameraPose pose = decodePose(body);
    if (time == null || pose == null) {
      return null;
    }
    try {
      return new CameraKeyframe(time, pose);
    } catch (IllegalArgumentException invalid) {
      // Drop the malformed keyframe rather than failing the document.
      return null;
    }
  }

  private static OverlayCue decodeShader(String body) {
    String id = stringField(body, "id");
    Double start = numberField(body, "start");
    Double end = numberField(body, "end");
    if (id == null || start == null || end == null) {
      return null;
    }
    try {
      return new OverlayCue(id, start, end);
    } catch (IllegalArgumentException invalid) {
      // Drop the malformed overlay cue rather than failing the document.
      return null;
    }
  }

  private static PropCue decodeProp(String body) {
    String id = stringField(body, "id");
    Double start = numberField(body, "start");
    Double end = numberField(body, "end");
    CameraPose pose = decodePose(body);
    if (id == null || start == null || end == null || pose == null) {
      return null;
    }
    try {
      return new PropCue(id, start, end, pose);
    } catch (IllegalArgumentException invalid) {
      // Drop the malformed prop cue rather than failing the document.
      return null;
    }
  }

  private static CameraPose decodePose(String body) {
    String world = stringField(body, "world");
    Double x = numberField(body, "x");
    Double y = numberField(body, "y");
    Double z = numberField(body, "z");
    Double yaw = numberField(body, "yaw");
    Double pitch = numberField(body, "pitch");
    if (world == null || x == null || y == null || z == null || yaw == null || pitch == null) {
      return null;
    }
    try {
      return new CameraPose(world, x, y, z, yaw.floatValue(), pitch.floatValue());
    } catch (IllegalArgumentException invalid) {
      // Drop the malformed pose rather than failing the document.
      return null;
    }
  }

  private static String stringField(String json, String field) {
    Matcher matcher = STRING_FIELD.matcher(json);
    while (matcher.find()) {
      if (field.equals(matcher.group(1))) {
        return unescapeJsonString(matcher.group(2));
      }
    }
    return null;
  }

  private static Double numberField(String json, String field) {
    Matcher matcher = NUMBER_FIELD.matcher(json);
    while (matcher.find()) {
      if (field.equals(matcher.group(1))) {
        try {
          return Double.parseDouble(matcher.group(2));
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  static List<String> objectArrayBodies(String json, String fieldName) {
    String body = arrayBodyOf(json, fieldName);
    List<String> objects = new ArrayList<>();
    int depth = 0;
    int start = -1;
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        if (depth == 0) {
          start = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          objects.add(body.substring(start, i + 1));
          start = -1;
        }
      }
    }
    return objects;
  }

  private static String arrayBodyOf(String json, String fieldName) {
    Pattern start = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[");
    Matcher startMatch = start.matcher(json);
    if (!startMatch.find()) {
      return "";
    }
    int from = startMatch.end();
    int depth = 1;
    boolean inString = false;
    boolean escaped = false;
    for (int i = from; i < json.length(); i++) {
      char c = json.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '[') {
        depth++;
      } else if (c == ']') {
        depth--;
        if (depth == 0) {
          return json.substring(from, i);
        }
      }
    }
    return json.substring(from);
  }

  private static <T> void appendJoined(
      StringBuilder sb, List<T> values, java.util.function.Function<T, String> encode) {
    for (int i = 0; i < values.size(); i++) {
      sb.append(encode.apply(values.get(i)));
      if (i + 1 < values.size()) {
        sb.append(',');
      }
      sb.append('\n');
    }
  }

  private static String unescapeJsonString(String raw) {
    if (raw.indexOf('\\') < 0) {
      return raw;
    }
    StringBuilder sb = new StringBuilder(raw.length());
    int i = 0;
    while (i < raw.length()) {
      char c = raw.charAt(i);
      if (c != '\\' || i + 1 >= raw.length()) {
        sb.append(c);
        i++;
        continue;
      }
      i++;
      char next = raw.charAt(i);
      switch (next) {
        case '"' -> sb.append('"');
        case '\\' -> sb.append('\\');
        case '/' -> sb.append('/');
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'u' -> {
          if (i + 4 < raw.length()) {
            try {
              sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
              i += 4;
            } catch (NumberFormatException e) {
              sb.append('u');
            }
          } else {
            sb.append('u');
          }
        }
        default -> sb.append(next);
      }
      i++;
    }
    return sb.toString();
  }

  private static String encodeString(String value) {
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
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
    sb.append('"');
    return sb.toString();
  }
}
