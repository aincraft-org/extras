package dev.mintychochip;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural check that shipped plugin metadata declares the party host + Paper API. */
class PluginDescriptorTest {

    @Test
    void paperPluginYmlDeclaresMainAndFoliaSupport() throws Exception {
        try (InputStream in = Objects.requireNonNull(
                PluginDescriptorTest.class.getClassLoader().getResourceAsStream("paper-plugin.yml"),
                "paper-plugin.yml missing")) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("main: dev.mintychochip.ExtrasPlugin"),
                    "main class should be ExtrasPlugin");
            assertFalse(yaml.contains("dev.jlo.mailbox.paper.MailboxPlugin"),
                    "standalone mailbox main class must not remain in the descriptor");
            assertTrue(yaml.contains("folia-supported: true"),
                    "folia-supported should be true");
            assertTrue(yaml.contains("api-version: '1.21'"),
                    "api-version should be 1.21");
            assertTrue(yaml.contains("extras.mail.use:"),
                    "mail permission should be declared");
            assertTrue(yaml.contains("default: true"),
                    "mail permission should be granted by default");
        }
    }

    @Test
    void mainClassExtendsJavaPlugin() throws ClassNotFoundException {
        Class<?> main = Class.forName("dev.mintychochip.ExtrasPlugin");
        assertTrue(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(main));
    }
}
