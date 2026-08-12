package dev.mintychochip.paper;

import dev.mintychochip.api.MailMessage;
import dev.mintychochip.api.MailService;
import dev.mintychochip.api.MailboxView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The mailbox chest GUI: paged list of mail, read on left-click, claim attachments on right-click,
 * prev/next page, and delete-read.
 *
 * <p>Folia-safe: no global schedulers; everything happens in the click event on the owning region's
 * thread. The {@link #clickListener(MailService)} instance is registered once in {@link
 * dev.mintychochip.ExtrasPlugin} and holds the service directly.
 */
public final class MailboxGui {

  static final int PAGE_SIZE = 12;

  /** Slots 0..44 hold mail items; 45..53 is the nav row. */
  private static final int MAIL_SLOTS_END = 45;

  private static final int PREV_SLOT = 45;
  private static final int NEXT_SLOT = 53;
  private static final int DELETE_SLOT = 49;
  private static final String TITLE_PREFIX = "Mailbox (page ";
  private static final java.time.format.DateTimeFormatter TIME_FORMAT =
      java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.ROOT);

  private MailboxGui() {}

  /** Inventory and view snapshot per player UUID, for slot→mail mapping. */
  private record MailboxSession(Inventory inventory, MailboxView view) {}

  private static final Map<UUID, MailboxSession> LATEST_SESSIONS = new ConcurrentHashMap<>();

  /** Opens the mailbox for {@code player} showing page 0. */
  public static void open(Player player, MailService service) {
    MailboxView view = service.mailbox(player.getUniqueId(), 0, PAGE_SIZE);
    buildAndOpen(player, view);
  }

  private static void buildAndOpen(Player player, MailboxView view) {
    Inventory inv = Bukkit.createInventory(player, 54, TITLE_PREFIX + (view.page() + 1) + ")");
    int i = 0;
    for (MailMessage mail : view.messages()) {
      inv.setItem(i++, mailItem(mail));
    }
    navItems(inv, view);
    player.openInventory(inv);
    LATEST_SESSIONS.put(player.getUniqueId(), new MailboxSession(inv, view));
  }

  private static ItemStack mailItem(MailMessage mail) {
    Material material = mail.attachment() != null ? Material.CHEST : Material.PAPER;
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(
        (mail.read() ? ChatColor.GRAY : ChatColor.YELLOW)
            + (mail.read() ? "" : "* ")
            + truncate(mail.body(), 32));
    List<String> lore = new ArrayList<>();
    lore.add(ChatColor.GRAY + "From: " + mail.senderName());
    lore.add(
        ChatColor.GRAY
            + TIME_FORMAT.format(
                java.time.Instant.ofEpochMilli(mail.sentAtMillis())
                    .atZone(java.time.ZoneId.systemDefault())));
    if (mail.attachment() != null) {
      lore.add(ChatColor.GOLD + "Right-click to claim item");
    }
    lore.add(ChatColor.DARK_GRAY + "Left-click to read (#" + mail.id() + ")");
    meta.setLore(lore);
    if (!item.setItemMeta(meta)) {
      return new ItemStack(material);
    }
    return item;
  }

  private static void navItems(Inventory inv, MailboxView view) {
    if (view.page() > 0) {
      ItemStack prev = new ItemStack(Material.ARROW);
      ItemMeta prevMeta = prev.getItemMeta();
      prevMeta.setDisplayName(ChatColor.GREEN + "Previous page");
      if (!prev.setItemMeta(prevMeta)) {
        return;
      }
      inv.setItem(PREV_SLOT, prev);
    }
    if (view.total() > (view.page() + 1) * view.pageSize()) {
      ItemStack next = new ItemStack(Material.ARROW);
      ItemMeta nextMeta = next.getItemMeta();
      nextMeta.setDisplayName(ChatColor.GREEN + "Next page");
      if (!next.setItemMeta(nextMeta)) {
        return;
      }
      inv.setItem(NEXT_SLOT, next);
    }
    ItemStack del = new ItemStack(Material.BARRIER);
    ItemMeta delMeta = del.getItemMeta();
    delMeta.setDisplayName(ChatColor.RED + "Delete read mail");
    if (!del.setItemMeta(delMeta)) {
      return;
    }
    inv.setItem(DELETE_SLOT, del);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  /** The shared click listener; register once in {@link dev.mintychochip.ExtrasPlugin}. */
  public static Listener clickListener(MailService service) {
    return new MailboxClickListener(service);
  }

  /** Clears cached mailbox views when the hosting plugin is disabled. */
  public static void clearViews() {
    LATEST_SESSIONS.clear();
  }

  private static final class MailboxClickListener implements Listener {

    private final MailService service;

    MailboxClickListener(MailService service) {
      this.service = service;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
        return;
      }
      String title = event.getView().getTitle();
      if (title == null || !title.startsWith(TITLE_PREFIX)) {
        return;
      }
      event.setCancelled(true);
      UUID playerId = player.getUniqueId();

      MailboxSession session = LATEST_SESSIONS.get(playerId);
      MailboxView view = session == null ? null : session.view();
      if (view == null) {
        player.closeInventory();
        return;
      }

      int slot = event.getRawSlot();
      if (slot >= 0 && slot < MAIL_SLOTS_END) {
        List<MailMessage> messages = view.messages();
        if (slot < messages.size()) {
          MailMessage mail = messages.get(slot);
          if (event.isRightClick()) {
            claim(player, mail);
          } else {
            read(player, mail);
          }
        }
        return;
      }
      if (slot == PREV_SLOT && view.page() > 0) {
        MailboxView prev = service.mailbox(playerId, view.page() - 1, PAGE_SIZE);
        buildAndOpen(player, prev);
        return;
      }
      if (slot == NEXT_SLOT && view.total() > (view.page() + 1) * view.pageSize()) {
        MailboxView next = service.mailbox(playerId, view.page() + 1, PAGE_SIZE);
        buildAndOpen(player, next);
        return;
      }
      if (slot == DELETE_SLOT) {
        deleteRead(player);
      }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
      String title = event.getView().getTitle();
      if (title != null && title.startsWith(TITLE_PREFIX)) {
        event.setCancelled(true);
      }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
        return;
      }
      MailboxSession current = LATEST_SESSIONS.get(player.getUniqueId());
      if (current != null && event.getInventory() == current.inventory()) {
        LATEST_SESSIONS.remove(player.getUniqueId());
      }
    }

    private void read(Player player, MailMessage mail) {
      if (!mail.read()) {
        service.markRead(player.getUniqueId(), mail.id());
      }
      player.sendMessage(
          ChatColor.GOLD
              + "[Mail #"
              + mail.id()
              + "] From "
              + mail.senderName()
              + " ("
              + TIME_FORMAT.format(
                  java.time.Instant.ofEpochMilli(mail.sentAtMillis())
                      .atZone(java.time.ZoneId.systemDefault()))
              + ")");
      player.sendMessage(mail.body());
    }

    private void claim(Player player, MailMessage mail) {
      if (mail.attachment() == null) {
        player.sendMessage(ChatColor.GRAY + "This mail has no attachment.");
        return;
      }
      // Decode FIRST: a corrupt blob must not be marked claimed (safe
      // degradation — the message stays readable, the item just can't be
      // restored, and it could still be repaired later).
      Optional<ItemStack> decoded = MailboxItemCodec.decode(mail.attachment());
      if (decoded.isEmpty()) {
        player.sendMessage(
            ChatColor.RED + "Attachment could not be restored (corrupt or unsupported).");
        return;
      }
      // Claim (atomic, guarded) only after we know the blob is decodable.
      Optional<String> blob = service.claimAttachment(player.getUniqueId(), mail.id());
      if (blob.isEmpty()) {
        player.sendMessage(ChatColor.GRAY + "Attachment already claimed or unavailable.");
        return;
      }
      ItemStack item = decoded.get();
      java.util.Map<Integer, ItemStack> leftoverMap = player.getInventory().addItem(item.clone());
      boolean dropped = !leftoverMap.isEmpty();
      for (ItemStack leftover : leftoverMap.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }
      player.sendMessage(
          ChatColor.GREEN
              + "Claimed "
              + item.getType().name().toLowerCase(java.util.Locale.ROOT)
              + (item.getAmount() > 1 ? " x" + item.getAmount() : "")
              + (dropped ? " (inventory full — dropped at your feet)" : "")
              + ".");
    }

    private void deleteRead(Player player) {
      UUID playerId = player.getUniqueId();
      int deleted = service.deleteAllRead(playerId);
      player.sendMessage(
          deleted == 0
              ? "No read mail to delete."
              : "Deleted " + deleted + " read mail message(s).");
      open(player, service);
    }
  }
}
