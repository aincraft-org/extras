package dev.mintychochip.paper;

import dev.mintychochip.api.MailService;
import dev.mintychochip.api.SendMailResult;
import java.util.Map;
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
 * The compose-mail chest GUI: shows the recipient + message preview, lets the player attach a held
 * item by moving the exact stack, and confirms send.
 *
 * <p>Folia-safe: no global schedulers. The shared listener is registered once in {@link
 * dev.mintychochip.ExtrasPlugin} (one instance handles mailbox + compose).
 */
public final class ComposeGui {

  private static final int PREVIEW_SLOT = 0;
  private static final int ATTACH_SLOT = 4;
  private static final int SEND_SLOT = 8;
  private static final String TITLE_PREFIX = "Compose mail to ";

  private ComposeGui() {}

  /**
   * Opens the compose view for {@code player}, addressed to {@code recipientId} (displayed as
   * {@code recipientName}) with the pre-typed {@code body}.
   */
  public static void open(
      Player player, MailService service, UUID recipientId, String recipientName, String body) {
    Inventory inv = Bukkit.createInventory(player, 9, TITLE_PREFIX + recipientName);
    ItemStack preview = new ItemStack(Material.PAPER);
    ItemMeta previewMeta = java.util.Objects.requireNonNull(preview.getItemMeta(), "item meta");
    previewMeta.setDisplayName(ChatColor.GOLD + "Message");
    previewMeta.setLore(
        java.util.List.of(ChatColor.GRAY + "To: " + recipientName, ChatColor.GRAY + body));
    if (!preview.setItemMeta(previewMeta)) {
      throw new IllegalStateException("Failed to set preview item meta");
    }
    inv.setItem(PREVIEW_SLOT, preview);

    ItemStack attach = new ItemStack(Material.CHEST);
    ItemMeta attachMeta = java.util.Objects.requireNonNull(attach.getItemMeta(), "item meta");
    attachMeta.setDisplayName(ChatColor.GREEN + "Attach held item");
    attachMeta.setLore(
        java.util.List.of(ChatColor.GRAY + "Hold an item, then click any inventory slot."));
    if (!attach.setItemMeta(attachMeta)) {
      throw new IllegalStateException("Failed to set attach item meta");
    }
    inv.setItem(ATTACH_SLOT, attach);

    ItemStack send = new ItemStack(Material.EMERALD);
    ItemMeta sendMeta = java.util.Objects.requireNonNull(send.getItemMeta(), "item meta");
    sendMeta.setDisplayName(ChatColor.GREEN + "Send");
    sendMeta.setLore(java.util.List.of(ChatColor.GRAY + "Deliver this mail."));
    if (!send.setItemMeta(sendMeta)) {
      throw new IllegalStateException("Failed to set send item meta");
    }
    inv.setItem(SEND_SLOT, send);

    player.openInventory(inv);
    SESSIONS.put(
        player.getUniqueId(), new ComposeSession(service, recipientId, recipientName, body, false));
  }

  /** The shared click/close listener; register once in {@link dev.mintychochip.ExtrasPlugin}. */
  public static Listener listener() {
    return new ComposeListener();
  }

  /**
   * Returns attached items and clears active compose sessions during plugin shutdown. The caller
   * must invoke this while the listener is still registered.
   */
  public static void closeActiveSessions() {
    for (UUID playerId : java.util.List.copyOf(SESSIONS.keySet())) {
      ComposeSession session = SESSIONS.remove(playerId);
      if (session == null) {
        continue;
      }
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !isComposeTitle(player.getOpenInventory().getTitle())) {
        continue;
      }
      if (session.attached()) {
        ItemStack attached = player.getOpenInventory().getTopInventory().getItem(ATTACH_SLOT);
        returnItem(player, attached);
      }
      player.closeInventory();
    }
  }

  private static void returnItem(Player player, ItemStack attached) {
    if (!MailboxItemCodec.hasAttachment(attached)) {
      return;
    }
    Map<Integer, ItemStack> leftoverMap = player.getInventory().addItem(attached.clone());
    for (ItemStack leftover : leftoverMap.values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
  }

  /** Whether {@code title} identifies a compose view (for the shared close handler). */
  static boolean isComposeTitle(String title) {
    return title != null && title.startsWith(TITLE_PREFIX);
  }

  /** Whether the event's view is a compose view (null-safe on the Paper view). */
  private static boolean isComposeView(InventoryClickEvent event) {
    org.bukkit.inventory.InventoryView view =
        java.util.Objects.requireNonNull(event.getView(), "event view");
    return view.getTitle() != null && view.getTitle().startsWith(TITLE_PREFIX);
  }

  private record ComposeSession(
      MailService service, UUID recipientId, String recipientName, String body, boolean attached) {

    private ComposeSession withAttachment() {
      return new ComposeSession(service, recipientId, recipientName, body, true);
    }
  }

  /** Player UUID -> active compose session (UUID keys only, no player refs). */
  private static final Map<UUID, ComposeSession> SESSIONS = new ConcurrentHashMap<>();

  private static final class ComposeListener implements Listener {

    @EventHandler
    void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
        return;
      }
      if (!isComposeView(event)) {
        return;
      }
      event.setCancelled(true);

      ComposeSession session = SESSIONS.get(player.getUniqueId());
      if (session == null) {
        player.closeInventory();
        return;
      }

      int slot = event.getRawSlot();
      if (slot == SEND_SLOT) {
        send(player, session);
        return;
      }
      // Only clicks in the player's own bottom inventory attach the held item.
      if (event.getClickedInventory() != null
          && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
          player.sendMessage(
              ChatColor.GRAY + "Hold an item first, then click a slot to attach it.");
          return;
        }
        ItemStack moved = held.clone();
        held.setAmount(0); // remove the moved stack from the inventory
        if (session.attached()) {
          ItemStack previous = event.getInventory().getItem(ATTACH_SLOT);
          event.getInventory().setItem(ATTACH_SLOT, null);
          returnItem(player, previous);
        }
        event.getInventory().setItem(ATTACH_SLOT, moved);
        player.sendMessage(
            ChatColor.GREEN
                + "Attached "
                + moved.getType().name().toLowerCase(java.util.Locale.ROOT)
                + (moved.getAmount() > 1 ? " x" + moved.getAmount() : "")
                + ".");
        SESSIONS.computeIfPresent(player.getUniqueId(), (id, current) -> current.withAttachment());
      }
    }

    @EventHandler
    void onInventoryDrag(InventoryDragEvent event) {
      if (isComposeTitle(event.getView().getTitle())) {
        event.setCancelled(true);
      }
    }

    @EventHandler
    void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
        return;
      }
      if (!isComposeTitle(event.getView().getTitle())) {
        return;
      }
      ComposeSession session = SESSIONS.remove(player.getUniqueId());
      if (session == null || !session.attached()) {
        return; // already sent or no item was attached
      }
      // Player closed without sending: return the moved item if any.
      ItemStack attached = event.getInventory().getItem(ATTACH_SLOT);
      returnItem(player, attached);
    }

    private void send(Player player, ComposeSession session) {
      ItemStack attached = player.getOpenInventory().getTopInventory().getItem(ATTACH_SLOT);
      String blob = session.attached() ? MailboxItemCodec.encode(attached) : null;
      SendMailResult result =
          session
              .service()
              .send(
                  player.getUniqueId(),
                  session.recipientId(),
                  player.getName(),
                  session.body(),
                  blob);
      // Guard cleared BEFORE closeInventory() so the close handler sees no
      // session and doesn't return the item on the send path.
      SESSIONS.remove(player.getUniqueId());
      player.closeInventory();

      String message =
          switch (result) {
            case SUCCESS -> ChatColor.GREEN + "Mail sent to " + session.recipientName() + ".";
            case INVALID_MESSAGE -> {
              if (session.attached()) {
                returnItem(player, attached);
              }
              yield ChatColor.RED + "Message is invalid (blank or too long).";
            }
            case SELF_MAIL -> {
              if (session.attached()) {
                returnItem(player, attached);
              }
              yield ChatColor.RED + "You cannot send mail to yourself.";
            }
          };
      player.sendMessage(message);
    }
  }
}
