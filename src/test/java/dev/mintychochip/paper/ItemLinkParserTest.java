package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemLinkParserTest {

  @Test
  void parsesCaseInsensitiveSingleTokenAndPreservesSurroundingText() {
    ItemLinkParser.ItemLinkParseResult result = ItemLinkParser.parse("before %ITEM after");

    assertEquals(ItemLinkParser.Kind.ONE_TOKEN, result.kind());
    assertEquals("before ", result.before());
    assertEquals(" after", result.after());
  }

  @Test
  void escapedTokenBecomesLiteralText() {
    ItemLinkParser.ItemLinkParseResult result = ItemLinkParser.parse("%%item");

    assertEquals(ItemLinkParser.Kind.ESCAPED_LITERAL, result.kind());
    assertEquals("%item", result.before());
    assertEquals("", result.after());
  }

  @Test
  void escapedTokensRemainLiteralAroundRealToken() {
    ItemLinkParser.ItemLinkParseResult result =
        ItemLinkParser.parse("left %%item middle %item right %%ITEM");

    assertEquals(ItemLinkParser.Kind.ONE_TOKEN, result.kind());
    assertEquals("left %item middle ", result.before());
    assertEquals(" right %item", result.after());
  }

  @Test
  void secondTokenIsRejected() {
    ItemLinkParser.ItemLinkParseResult result = ItemLinkParser.parse("%item and %ITEM");

    assertEquals(ItemLinkParser.Kind.TOO_MANY_TOKENS, result.kind());
  }

  @Test
  void ordinaryTextHasNoToken() {
    ItemLinkParser.ItemLinkParseResult result = ItemLinkParser.parse("ordinary text");

    assertEquals(ItemLinkParser.Kind.NO_TOKEN, result.kind());
    assertTrue(result.before().equals("ordinary text"));
  }
}
