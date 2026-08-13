package dev.mintychochip.paper;

import dev.mintychochip.api.ChannelId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Renders server-authored channel decoration and native item hover links. */
public final class ChatFormatter {
  private static final Map<ChannelId, ChannelStyle> STYLES =
      Map.of(
          ChannelId.GLOBAL,
          new ChannelStyle("[G]", NamedTextColor.GRAY),
          ChannelId.LOCAL,
          new ChannelStyle("[L]", NamedTextColor.YELLOW),
          ChannelId.PARTY,
          new ChannelStyle("[P]", NamedTextColor.AQUA),
          ChannelId.MARKET,
          new ChannelStyle("[MARKET]", NamedTextColor.GOLD),
          ChannelId.LFG,
          new ChannelStyle("[LFG]", NamedTextColor.GREEN));

  private ChatFormatter() {}

  public static Component format(
      ChannelId channel,
      Component title,
      Component displayName,
      Component message,
      ItemStack heldItem) {
    Objects.requireNonNull(channel);
    Objects.requireNonNull(title);
    Objects.requireNonNull(displayName);
    Objects.requireNonNull(message);
    ChannelStyle style = STYLES.get(channel);
    Component prefix = Component.text(style.label(), style.color());
    if (!title.equals(Component.empty())) prefix = prefix.append(Component.space()).append(title);
    prefix = prefix.append(Component.space()).append(displayName).append(Component.text(": "));
    return prefix.append(renderMessage(message, heldItem));
  }

  private static Component renderMessage(Component message, ItemStack heldItem) {
    int tokenCount = countTokens(message);
    if (tokenCount > 1) return message;
    if (tokenCount == 0) return normalizeEscapes(message);
    if (heldItem == null || heldItem.getType() == Material.AIR)
      throw new IllegalStateException("Cannot render %item without a held item");
    ItemStack clone = heldItem.clone();
    Component itemName =
        Component.text(
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(clone.effectiveName()));
    Component itemLink =
        Component.text("[")
            .append(itemName)
            .append(Component.text(" x" + clone.getAmount() + "]"))
            .hoverEvent(clone.asHoverEvent());
    return replaceItemToken(message, itemLink);
  }

  static Component replaceItemToken(Component message, Component replacement) {
    return countTokens(message) != 1 ? normalizeEscapes(message) : transform(message, replacement);
  }

  private static int countTokens(Component component) {
    int count = component instanceof TextComponent text ? countRealTokens(text.content()) : 0;
    for (Component child : component.children()) count += countTokens(child);
    return count;
  }

  private static int countRealTokens(String text) {
    return ItemLinkParser.parse(text).kind() == ItemLinkParser.Kind.ONE_TOKEN ? 1 : 0;
  }

  private static Component normalizeEscapes(Component component) {
    Component result = component;
    if (component instanceof TextComponent text) {
      String normalized = ItemLinkParser.parse(text.content()).before();
      if (!normalized.equals(text.content())) result = text.toBuilder().content(normalized).build();
    }
    if (!component.children().isEmpty()) {
      List<Component> children = new ArrayList<>(component.children().size());
      for (Component child : component.children()) children.add(normalizeEscapes(child));
      result = result.children(children);
    }
    return result;
  }

  private static Component transform(Component component, Component replacement) {
    if (!(component instanceof TextComponent text)) {
      if (component.children().isEmpty()) return component;
      List<Component> children = new ArrayList<>(component.children().size());
      for (Component child : component.children()) children.add(transform(child, replacement));
      return component.children(children);
    }
    ItemLinkParser.ItemLinkParseResult parsed = ItemLinkParser.parse(text.content());
    if (parsed.kind() == ItemLinkParser.Kind.ONE_TOKEN) {
      Component result =
          styledText(text, parsed.before())
              .append(
                  replacement.style(
                      replacement
                          .style()
                          .merge(
                              text.style(),
                              net.kyori.adventure.text.format.Style.Merge.Strategy
                                  .IF_ABSENT_ON_TARGET,
                              net.kyori.adventure.text.format.Style.Merge.COLOR,
                              net.kyori.adventure.text.format.Style.Merge.DECORATIONS,
                              net.kyori.adventure.text.format.Style.Merge.EVENTS,
                              net.kyori.adventure.text.format.Style.Merge.INSERTION,
                              net.kyori.adventure.text.format.Style.Merge.FONT,
                              net.kyori.adventure.text.format.Style.Merge.SHADOW_COLOR)))
              .append(styledText(text, parsed.after()));
      for (Component child : text.children()) result = result.append(transform(child, replacement));
      return result;
    }
    Component result =
        parsed.kind() == ItemLinkParser.Kind.ESCAPED_LITERAL
            ? text.toBuilder().content(parsed.before()).build()
            : text;
    if (!text.children().isEmpty()) {
      List<Component> children = new ArrayList<>(text.children().size());
      for (Component child : text.children()) children.add(transform(child, replacement));
      result = result.children(children);
    }
    return result;
  }

  private static Component styledText(TextComponent source, String content) {
    return content.isEmpty() ? Component.empty() : source.toBuilder().content(content).build();
  }

  private record ChannelStyle(String label, NamedTextColor color) {}
}
