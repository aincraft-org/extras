package dev.mintychochip.api.rewards;

import java.util.Objects;
import java.util.regex.Pattern;

/** Bukkit-free namespaced key used for blocks, entities, and items. */
public record MaterialKey(String namespace, String key) {

  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
  private static final Pattern KEY = Pattern.compile("[a-z0-9_./-]+");

  public MaterialKey {
    namespace = normalize(namespace, "namespace");
    key = normalize(key, "key");
    if (!NAMESPACE.matcher(namespace).matches()) {
      throw new IllegalArgumentException("namespace contains invalid characters: " + namespace);
    }
    if (!KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("key contains invalid characters: " + key);
    }
  }

  public static MaterialKey parse(String value) {
    Objects.requireNonNull(value, "value");
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    int separator = normalized.indexOf(':');
    if (separator < 1 || separator == normalized.length() - 1) {
      throw new IllegalArgumentException("material key must be namespace:key");
    }
    return new MaterialKey(normalized.substring(0, separator), normalized.substring(separator + 1));
  }

  @Override
  public String toString() {
    return namespace + ":" + key;
  }

  private static String normalize(String value, String field) {
    Objects.requireNonNull(value, field);
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }
}
