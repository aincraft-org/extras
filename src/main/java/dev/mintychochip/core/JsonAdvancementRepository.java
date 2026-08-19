package dev.mintychochip.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-backed {@link AdvancementRepository}: one JSON document per player under a data directory.
 *
 * <p>Documents store a {@code completed} string array. A missing or empty document decodes to no
 * completions. Corrupted values degrade to empty rather than failing the load.
 */
public final class JsonAdvancementRepository implements AdvancementRepository {

  private static final Pattern ARRAY_STRING_MEMBER = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

  private final Path dataDirectory;

  public JsonAdvancementRepository(Path dataDirectory) {
    this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    try {
      Files.createDirectories(dataDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to create advancement data directory: " + dataDirectory, e);
    }
  }

  @Override
  public Set<String> findCompleted(UUID playerId) {
    Path file = fileFor(playerId);
    if (!Files.isRegularFile(file)) {
      return Set.of();
    }
    try {
      return decode(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read advancement completions " + playerId, e);
    }
  }

  @Override
  public void save(UUID playerId, Set<String> completed) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(completed, "completed");
    Path file = fileFor(playerId);
    try {
      Files.createDirectories(dataDirectory);
      Files.writeString(file, encode(playerId, completed), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save advancement completions " + playerId, e);
    }
  }

  @Override
  public void close() {
    // File-backed store holds no open resources.
  }

  Path fileFor(UUID playerId) {
    return dataDirectory.resolve(playerId.toString() + ".json");
  }

  static String encode(UUID playerId, Collection<String> completed) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\n");
    sb.append("  \"playerId\": \"").append(playerId).append("\",\n");
    sb.append("  \"completed\": ").append(encodeStringArray(completed)).append('\n');
    sb.append("}\n");
    return sb.toString();
  }

  static Set<String> decode(String json) {
    Set<String> completed = new LinkedHashSet<>();
    String body = arrayBodyOf(json, "completed");
    Matcher member = ARRAY_STRING_MEMBER.matcher(body);
    while (member.find()) {
      completed.add(unescapeJsonString(member.group(1)));
    }
    return Collections.unmodifiableSet(completed);
  }

  private static String arrayBodyOf(String json, String fieldName) {
    Pattern start = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[");
    Matcher startMatch = start.matcher(json);
    if (!startMatch.find()) {
      return "";
    }
    int from = startMatch.end();
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
      } else if (c == '"') {
        inString = true;
      } else if (c == ']') {
        return json.substring(from, i);
      }
    }
    return json.substring(from);
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

  private static String encodeStringArray(Collection<String> values) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    boolean first = true;
    for (String value : values) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(encodeString(value));
    }
    sb.append(']');
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
