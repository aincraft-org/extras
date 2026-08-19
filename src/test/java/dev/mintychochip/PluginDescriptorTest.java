package dev.mintychochip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Structural check that shipped plugin metadata declares the party host + Paper API. */
class PluginDescriptorTest {

  @Test
  void paperPluginYmlDeclaresMainAndFoliaSupport() throws Exception {
    try (InputStream in =
        Objects.requireNonNull(
            PluginDescriptorTest.class.getClassLoader().getResourceAsStream("paper-plugin.yml"),
            "paper-plugin.yml missing")) {
      String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(
          yaml.contains("main: dev.mintychochip.ExtrasPlugin"),
          "main class should be ExtrasPlugin");
      assertFalse(
          yaml.contains("dev.jlo.mailbox.paper.MailboxPlugin"),
          "standalone mailbox main class must not remain in the descriptor");
      assertTrue(yaml.contains("folia-supported: true"), "folia-supported should be true");
      assertTrue(yaml.contains("api-version: '1.21'"), "api-version should be 1.21");
      assertTrue(
          yaml.contains(
              "description: Persistent parties, friendships, titles, custom advancements, player mailboxes, and item trading, and chat channels."),
          "plugin description should mention custom advancements");
      assertTrue(yaml.contains("extras.chat.use:"), "chat permission should be declared");
      assertTrue(yaml.contains("default: true"), "chat permission should be granted by default");
      assertTrue(yaml.contains("aliases: [ch, c]"), "chat aliases should be declared");
      assertTrue(yaml.contains("extras.rewards.use:"), "rewards use permission should be declared");
      assertTrue(
          yaml.contains("extras.rewards.admin:"), "rewards admin permission should be declared");
      assertTrue(
          yaml.contains("extras.advancements.use:"),
          "advancements use permission should be declared");
      assertTrue(
          yaml.contains("extras.advancements.admin:"),
          "advancements admin permission should be declared");
    }
  }

  @Test
  void mainClassExtendsJavaPlugin() throws ClassNotFoundException {
    Class<?> main = Class.forName("dev.mintychochip.ExtrasPlugin");
    assertTrue(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(main));
  }

  @Test
  void pluginHoldsAndRegistersEventService() throws Exception {
    Class<?> plugin = Class.forName("dev.mintychochip.ExtrasPlugin");
    plugin.getDeclaredField("eventService");
    Class<?> eventApi = Class.forName("dev.mintychochip.api.events.ExtrasEventService");
    assertTrue(eventApi.isInterface(), "ExtrasEventService should be an SPI interface");
    Class<?> subscription = Class.forName("dev.mintychochip.api.events.EventSubscription");
    assertTrue(
        java.lang.AutoCloseable.class.isAssignableFrom(subscription),
        "EventSubscription should be AutoCloseable");
  }

  @Test
  void pluginHoldsAndRegistersAdvancementService() throws Exception {
    Class<?> plugin = Class.forName("dev.mintychochip.ExtrasPlugin");
    plugin.getDeclaredField("advancementService");
    Class<?> api = Class.forName("dev.mintychochip.api.AdvancementService");
    assertTrue(api.isInterface(), "AdvancementService should be an SPI interface");
    Class<?> toastApi = Class.forName("dev.mintychochip.api.AdvancementToastSender");
    assertTrue(toastApi.isInterface(), "AdvancementToastSender should be the toast API");
    String source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/dev/mintychochip/ExtrasPlugin.java"));
    assertTrue(
        source.contains("register(AdvancementService.class"),
        "ExtrasPlugin should register AdvancementService on ServicesManager");
    assertTrue(
        source.contains("register(AdvancementToastSender.class"),
        "ExtrasPlugin should register AdvancementToastSender on ServicesManager");
    assertTrue(
        source.contains("AdvancementToastListener"),
        "ExtrasPlugin should wire the grant toast listener");
    assertTrue(
        source.contains("new AdvancementCommand(advancementService)"),
        "ExtrasPlugin should register the advancements command");
  }
}
