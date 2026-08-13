package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.mintychochip.api.ChannelId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ChatFormatterTest {

  @Test
  void replacesTokenMergesAbsentSourceStyleWithoutOverwritingReplacementHover() {
    ClickEvent sourceClick = ClickEvent.runCommand("/source");
    HoverEvent<Component> replacementHover = HoverEvent.showText(Component.text("replacement"));
    Component message =
        Component.text("", NamedTextColor.RED)
            .append(Component.text("%item").decorate(TextDecoration.BOLD).clickEvent(sourceClick));
    Component replacement = Component.text("[item]").hoverEvent(replacementHover);

    Component formatted = ChatFormatter.replaceItemToken(message, replacement);

    assertEquals(NamedTextColor.RED, formatted.color());
    Component transformedItem = findByHover(formatted, replacementHover);
    assertEquals(TextDecoration.State.TRUE, transformedItem.decoration(TextDecoration.BOLD));
    assertEquals(sourceClick, transformedItem.clickEvent());
    assertEquals(replacementHover, transformedItem.hoverEvent());
  }

  @Test
  void replacesTokenWithoutFlatteningStyledText() {
    Component message =
        Component.text("before ", NamedTextColor.RED)
            .append(Component.text("%item").decorate(TextDecoration.BOLD))
            .append(Component.text(" after", NamedTextColor.RED));

    Component formatted = ChatFormatter.replaceItemToken(message, Component.text("[item]"));

    assertEquals(
        "before [item] after", PlainTextComponentSerializer.plainText().serialize(formatted));
    assertEquals(NamedTextColor.RED, formatted.color());
    assertEquals(
        TextDecoration.State.TRUE, findByText(formatted, "[item]").decoration(TextDecoration.BOLD));
  }

  @Test
  void rendersExactChannelLabelsAndColors() {
    assertChannel(ChannelId.GLOBAL, "[G]", NamedTextColor.GRAY);
    assertChannel(ChannelId.LOCAL, "[L]", NamedTextColor.YELLOW);
    assertChannel(ChannelId.PARTY, "[P]", NamedTextColor.AQUA);
    assertChannel(ChannelId.MARKET, "[MARKET]", NamedTextColor.GOLD);
    assertChannel(ChannelId.LFG, "[LFG]", NamedTextColor.GREEN);
  }

  @Test
  void optionalTitleIsIncludedBeforeDisplayName() {
    Component formatted =
        ChatFormatter.format(
            ChannelId.GLOBAL,
            Component.text("[VIP]"),
            Component.text("Alice"),
            Component.text("hello"),
            null);

    assertTrue(
        PlainTextComponentSerializer.plainText()
            .serialize(formatted)
            .contains("[VIP] Alice: hello"));
  }

  @Test
  void itemLinkUsesClonedStackHoverEventWithoutMutatingOriginal() {
    assumeTrue(
        Bukkit.getServer() != null, "Requires a running Bukkit server (skipped in headless JUnit)");
    ItemStack held = new ItemStack(Material.DIAMOND, 3);
    Component formatted =
        ChatFormatter.format(
            ChannelId.GLOBAL,
            Component.empty(),
            Component.text("Alice"),
            Component.text("show %item"),
            held);

    assertEquals(3, held.getAmount());
    assertTrue(
        PlainTextComponentSerializer.plainText().serialize(formatted).contains("[Diamond x3]"));
  }

  @Test
  void escapedTokenIsVisibleWithoutHeldItem() {
    Component formatted =
        ChatFormatter.format(
            ChannelId.GLOBAL,
            Component.empty(),
            Component.text("Alice"),
            Component.text("show %%item"),
            null);

    assertTrue(
        PlainTextComponentSerializer.plainText().serialize(formatted).endsWith("show %item"));
  }

  @Test
  void missingHeldItemIsRejectedForRealToken() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            ChatFormatter.format(
                ChannelId.GLOBAL,
                Component.empty(),
                Component.text("Alice"),
                Component.text("show %item"),
                null));
  }

  private static Component findByHover(Component component, HoverEvent<Component> expectedHover) {
    if (expectedHover.equals(component.hoverEvent())) {
      return component;
    }
    for (Component child : component.children()) {
      Component found = findByHoverOrNull(child, expectedHover);
      if (found != null) {
        return found;
      }
    }
    throw new AssertionError("Component with expected hover event not found");
  }

  private static Component findByHoverOrNull(
      Component component, HoverEvent<Component> expectedHover) {
    if (expectedHover.equals(component.hoverEvent())) {
      return component;
    }
    for (Component child : component.children()) {
      Component found = findByHoverOrNull(child, expectedHover);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static Component findByText(Component component, String text) {
    if (component instanceof TextComponent textComponent && text.equals(textComponent.content())) {
      return component;
    }
    for (Component child : component.children()) {
      try {
        return findByText(child, text);
      } catch (AssertionError ignored) {
        // Continue searching sibling components.
      }
    }
    throw new AssertionError("Text component not found: " + text);
  }

  private static void assertChannel(ChannelId channel, String label, NamedTextColor expectedColor) {
    Component formatted =
        ChatFormatter.format(
            channel, Component.empty(), Component.text("Alice"), Component.text("hello"), null);

    String plain = PlainTextComponentSerializer.plainText().serialize(formatted);
    assertTrue(plain.startsWith(label + " Alice: hello"), plain);
    assertEquals(expectedColor, formatted.color());
  }
}
