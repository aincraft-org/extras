package dev.mintychochip.core;

import dev.mintychochip.api.TitleProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-backed {@link TitleRepository}: one JSON document per player under a
 * data directory.
 *
 * <p>Uses a minimal hand-rolled JSON codec (no third-party dependencies),
 * mirroring the azoth character-profile repository. Documents are versioned
 * with a single {@code unlockedTitles} array and nullable {@code equippedTitle}
 * field; a missing or empty document decodes to an empty title state, and an
 * equipped title that is not unlocked is dropped on decode.
 */
public final class JsonTitleRepository implements TitleRepository {

    private static final Pattern STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ARRAY_STRING_MEMBER = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final Path dataDirectory;

    public JsonTitleRepository(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create title data directory: " + dataDirectory, e);
        }
    }

    @Override
    public Optional<TitleProfile> findById(UUID playerId) {
        Path file = fileFor(playerId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(decode(json, playerId));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read title profile " + playerId, e);
        }
    }

    @Override
    public void save(TitleProfile profile) {
        Path file = fileFor(profile.playerId());
        try {
            Files.createDirectories(dataDirectory);
            Files.writeString(file, encode(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save title profile " + profile.playerId(), e);
        }
    }

    @Override
    public void close() {
        // File-backed store holds no open resources.
    }

    Path fileFor(UUID playerId) {
        return dataDirectory.resolve(playerId.toString() + ".json");
    }

    static String encode(TitleProfile profile) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\n");
        sb.append("  \"playerId\": \"").append(profile.playerId()).append("\",\n");
        sb.append("  \"unlockedTitles\": ").append(encodeStringArray(profile.unlockedTitles())).append(",\n");
        sb.append("  \"equippedTitle\": ").append(encodeNullableString(profile.equippedTitle().orElse(null)))
                .append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    static TitleProfile decode(String json, UUID expectedId) {
        Set<String> unlockedTitles = new LinkedHashSet<>();

        // string array members of the unlockedTitles field
        for (String fieldName : stringArrayFieldNames(json)) {
            if ("unlockedTitles".equals(fieldName)) {
                parseStringArrayBody(json, fieldName, unlockedTitles);
            }
        }

        String equippedTitle = null;
        Matcher stringMatch = STRING_FIELD.matcher(json);
        Set<String> seenKeys = new LinkedHashSet<>();
        while (stringMatch.find()) {
            String key = stringMatch.group(1);
            if (!seenKeys.add(key)) {
                continue;
            }
            if ("equippedTitle".equals(key)) {
                equippedTitle = unescapeJsonString(stringMatch.group(2));
            }
        }

        if (equippedTitle != null && !unlockedTitles.contains(equippedTitle)) {
            equippedTitle = null; // equipped must be unlocked
        }

        return new TitleProfile(expectedId, unlockedTitles, equippedTitle);
    }

    /** Returns the names of array-valued string fields in order of appearance. */
    private static List<String> stringArrayFieldNames(String json) {
        List<String> names = new ArrayList<>();
        Matcher match = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[").matcher(json);
        while (match.find()) {
            names.add(match.group(1));
        }
        return names;
    }

    /** Parses the members of the array field {@code fieldName} into {@code target}. */
    private static void parseStringArrayBody(String json, String fieldName, Set<String> target) {
        String body = arrayBodyOf(json, fieldName);
        Matcher member = ARRAY_STRING_MEMBER.matcher(body);
        while (member.find()) {
            target.add(unescapeJsonString(member.group(1)));
        }
    }

    /** Returns the text inside the {@code [ … ]} of the named array field, verbatim. */
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

    /** Decodes JSON escapes inside a string literal body (no surrounding quotes). */
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

    private static String encodeNullableString(String value) {
        return value == null ? "null" : encodeString(value);
    }

    /** JSON string literal with escaping of quotes, backslashes, and control chars. */
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
