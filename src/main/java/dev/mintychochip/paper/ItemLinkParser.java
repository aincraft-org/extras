package dev.mintychochip.paper;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the single supported item-link token in a chat message. */
public final class ItemLinkParser {

  private static final Pattern TOKEN = Pattern.compile("%item", Pattern.CASE_INSENSITIVE);

  private ItemLinkParser() {}

  public static ItemLinkParseResult parse(String message) {
    Objects.requireNonNull(message, "message");
    int tokenStart = -1;
    int tokenCount = 0;
    boolean escaped = false;
    for (int index = 0; index < message.length(); ) {
      if (index + 1 < message.length()
          && message.charAt(index) == '%'
          && message.charAt(index + 1) == '%'
          && TOKEN.matcher(message).region(index + 1, message.length()).lookingAt()) {
        escaped = true;
        index += 6;
        continue;
      }
      Matcher matcher = TOKEN.matcher(message).region(index, message.length());
      if (matcher.lookingAt()) {
        tokenCount++;
        if (tokenCount == 1) {
          tokenStart = index;
        }
        index = matcher.end();
      } else {
        index++;
      }
    }
    if (tokenCount > 1) {
      return new ItemLinkParseResult(Kind.TOO_MANY_TOKENS, normalizeEscapes(message), "");
    }
    if (tokenCount == 0) {
      return new ItemLinkParseResult(
          escaped ? Kind.ESCAPED_LITERAL : Kind.NO_TOKEN, normalizeEscapes(message), "");
    }
    return new ItemLinkParseResult(
        Kind.ONE_TOKEN,
        normalizeEscapes(message.substring(0, tokenStart)),
        normalizeEscapes(message.substring(tokenStart + 5)));
  }

  private static String normalizeEscapes(String text) {
    return text.replaceAll("%%(?i:item)", "%item");
  }

  public enum Kind {
    NO_TOKEN,
    ONE_TOKEN,
    ESCAPED_LITERAL,
    TOO_MANY_TOKENS
  }

  public record ItemLinkParseResult(Kind kind, String before, String after) {
    public ItemLinkParseResult {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(before, "before");
      Objects.requireNonNull(after, "after");
    }
  }
}
