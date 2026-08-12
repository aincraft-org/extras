package dev.mintychochip.paper;

import java.io.StringReader;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Encodes {@link ItemStack}s to an opaque, versioned blob for mail attachments and back. Uses
 * Bukkit's own {@link YamlConfiguration} serializer so enchants, display names, and other metadata
 * survive unchanged. The {@code format} discriminator guards against future format drift;
 * foreign/corrupt blobs degrade to {@link Optional#empty()} (never throw).
 */
public final class MailboxItemCodec {

  private static final int FORMAT = 1;

  private MailboxItemCodec() {}

  /** Returns the attachment blob for {@code item}, or {@code null} if air/null. */
  public static String encode(ItemStack item) {
    if (!hasAttachment(item)) {
      return null;
    }
    YamlConfiguration config = new YamlConfiguration();
    config.set("item", item.serialize());
    config.set("format", FORMAT);
    return config.saveToString();
  }

  /** Decodes an attachment blob back to an item, or {@link Optional#empty()}. */
  public static Optional<ItemStack> decode(String blob) {
    if (blob == null || blob.isBlank()) {
      return Optional.empty();
    }
    try {
      YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(blob));
      int format = config.getInt("format", -1);
      if (format != FORMAT) {
        Bukkit.getLogger()
            .log(Level.WARNING, "Ignoring mail attachment with unknown format: {0}", blob);
        return Optional.empty();
      }
      ConfigurationSection section = config.getConfigurationSection("item");
      if (section == null) {
        return Optional.empty();
      }
      Map<String, Object> map = section.getValues(false);
      ItemStack item = ItemStack.deserialize(map);
      if (item == null || item.getType() == Material.AIR) {
        return Optional.empty();
      }
      return Optional.of(item);
    } catch (RuntimeException e) {
      Bukkit.getLogger().log(Level.WARNING, "Failed to decode mail attachment", e);
      return Optional.empty();
    }
  }

  /** True when {@code item} is a real stack worth attaching. */
  public static boolean hasAttachment(ItemStack item) {
    return item != null && item.getType() != Material.AIR;
  }
}
