package dev.mintychochip.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MailboxItemCodecTest {

    /**
     * These tests exercise live Bukkit {@link ItemStack}/{@link Enchantment} and
     * {@link Bukkit#getLogger()} behaviour, which require a running Paper server
     * or a server-mock (none supports paper-api 1.21.11 yet). When run headless
     * (no server), they are skipped rather than failing; the round-trips are
     * exercised against a live server by the Paper runServer smoke.
     */
    @BeforeEach
    void requireServer() {
        assumeTrue(Bukkit.getServer() != null, "Requires a running Bukkit server (skipped in headless JUnit)");
    }

    @Test
    void roundTripsBasicStack() {
        ItemStack diamond = new ItemStack(Material.DIAMOND_PICKAXE, 1);
        Optional<ItemStack> decoded = MailboxItemCodec.decode(MailboxItemCodec.encode(diamond));
        assertTrue(decoded.isPresent());
        assertEquals(Material.DIAMOND_PICKAXE, decoded.get().getType());
        assertEquals(1, decoded.get().getAmount());
    }

    @Test
    void roundTripsEnchantedAndNamed() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD, 1);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("Bane of Bugs");
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        assertTrue(sword.setItemMeta(meta));

        Optional<ItemStack> decoded = MailboxItemCodec.decode(MailboxItemCodec.encode(sword));
        assertTrue(decoded.isPresent());
        assertEquals("Bane of Bugs", decoded.get().getItemMeta().getDisplayName());
        assertEquals(5, decoded.get().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void roundTripsStackedAmount() {
        ItemStack stack = new ItemStack(Material.ENDER_PEARL, 16);
        Optional<ItemStack> decoded = MailboxItemCodec.decode(MailboxItemCodec.encode(stack));
        assertTrue(decoded.isPresent());
        assertEquals(16, decoded.get().getAmount());
    }

    @Test
    void encodeNullIsNull() {
        assertNull(MailboxItemCodec.encode(null));
        assertNull(MailboxItemCodec.encode(new ItemStack(Material.AIR)));
    }

    @Test
    void decodeCorruptReturnsEmpty() {
        assertEquals(Optional.empty(), MailboxItemCodec.decode("not json"));
        assertEquals(Optional.empty(), MailboxItemCodec.decode("{\"format\":99}"));
        assertEquals(Optional.empty(), MailboxItemCodec.decode("{\"format\":1,\"item\":\"garbage\"}"));
        assertEquals(Optional.empty(), MailboxItemCodec.decode(null));
        assertEquals(Optional.empty(), MailboxItemCodec.decode(""));
    }

    @Test
    void hasAttachment() {
        assertTrue(MailboxItemCodec.hasAttachment(new ItemStack(Material.DIAMOND)));
        assertFalse(MailboxItemCodec.hasAttachment(new ItemStack(Material.AIR)));
        assertFalse(MailboxItemCodec.hasAttachment(null));
    }
}
