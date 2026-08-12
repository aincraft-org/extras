package dev.mintychochip.paper;

import dev.mintychochip.api.TradeResult;
import dev.mintychochip.api.TradeService;
import dev.mintychochip.api.TradeSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Two-player item trade inventory and lifecycle listener. */
public final class TradeGui {
  static final int INVENTORY_SIZE = 54;
  static final int FIRST_OFFER_START = 0;
  static final int FIRST_OFFER_END = 20;
  static final int SECOND_OFFER_START = 27;
  static final int SECOND_OFFER_END = 47;
  static final int FIRST_CONFIRM_SLOT = 22;
  static final int SECOND_CONFIRM_SLOT = 49;
  private static final String TITLE_PREFIX = "Trade: ";
  private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
  private static final Map<UUID, List<ItemStack>> PENDING_RETURNS = new ConcurrentHashMap<>();

  private TradeGui() {}

  public static void open(Player first, Player second, TradeService service) {
    TradeSnapshot snapshot = service.tradeOf(first.getUniqueId()).orElseThrow();
    Inventory firstInventory = createInventory(first, second.getName());
    Inventory secondInventory = createInventory(second, first.getName());
    Session session =
        new Session(
            service, first.getUniqueId(), second.getUniqueId(), firstInventory, secondInventory);
    SESSIONS.put(snapshot.tradeId(), session);
    SESSIONS.put(first.getUniqueId(), session);
    SESSIONS.put(second.getUniqueId(), session);
    first.openInventory(firstInventory);
    second.openInventory(secondInventory);
  }

  public static Listener listener(TradeService service) {
    return new TradeListener(service);
  }

  public static void closeActiveSessions() {
    for (Session session : List.copyOf(SESSIONS.values())) {
      if (SESSIONS.get(session.firstPlayerId()) == session) {
        cancel(session, "Trade cancelled.");
      }
    }
    SESSIONS.clear();
  }

  static boolean isOfferSlotFor(int viewer, int slot) {
    return viewer == 0
        ? slot >= FIRST_OFFER_START && slot <= FIRST_OFFER_END
        : viewer == 1 && slot >= SECOND_OFFER_START && slot <= SECOND_OFFER_END;
  }

  static boolean isTradeTitle(String title) {
    return title != null && title.startsWith(TITLE_PREFIX);
  }

  private static Inventory createInventory(Player viewer, String otherName) {
    Inventory inventory = Bukkit.createInventory(viewer, INVENTORY_SIZE, TITLE_PREFIX + otherName);
    fillSeparators(inventory);
    inventory.setItem(
        FIRST_CONFIRM_SLOT, control(Material.LIME_DYE, ChatColor.GREEN + "Confirm your offer"));
    inventory.setItem(
        SECOND_CONFIRM_SLOT, control(Material.LIME_DYE, ChatColor.GREEN + "Confirm your offer"));
    return inventory;
  }

  private static void fillSeparators(Inventory inventory) {
    ItemStack separator = control(Material.GRAY_STAINED_GLASS_PANE, " ");
    for (int slot = 21; slot <= 26; slot++) {
      inventory.setItem(slot, separator);
    }
    for (int slot = 48; slot <= 53; slot++) {
      inventory.setItem(slot, separator);
    }
  }

  private static ItemStack control(Material material, String name) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(name);
      item.setItemMeta(meta);
    }
    return item;
  }

  private static void handleClick(InventoryClickEvent event, TradeService service) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    Session session = SESSIONS.get(player.getUniqueId());
    if (session == null || !isTradeTitle(event.getView().getTitle())) {
      return;
    }
    event.setCancelled(true);
    int viewer = session.viewerIndex(player.getUniqueId());
    int rawSlot = event.getRawSlot();
    if (rawSlot == session.confirmSlot(viewer)) {
      confirm(player, session, service);
      return;
    }
    if (rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize()) {
      if (isOfferSlotFor(viewer, rawSlot)) {
        withdrawOffer(player, session, viewer, rawSlot, service);
      }
      return;
    }
    if (event.getClickedInventory() != null
        && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
      depositHeldItem(player, session, viewer, service);
    }
  }

  private static void depositHeldItem(
      Player player, Session session, int viewer, TradeService service) {
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held == null || held.getType() == Material.AIR) {
      player.sendMessage(ChatColor.GRAY + "Hold the item you want to offer in your main hand.");
      return;
    }
    Inventory inventory = session.inventoryFor(player.getUniqueId());
    int start = viewer == 0 ? FIRST_OFFER_START : SECOND_OFFER_START;
    int end = viewer == 0 ? FIRST_OFFER_END : SECOND_OFFER_END;
    for (int slot = start; slot <= end; slot++) {
      if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR) {
        ItemStack moved = held.clone();
        held.setAmount(0);
        inventory.setItem(slot, moved);
        service.offerChanged(player.getUniqueId());
        refresh(session, service);
        return;
      }
    }
    player.sendMessage(ChatColor.RED + "Your offer area is full.");
  }

  private static void withdrawOffer(
      Player player, Session session, int viewer, int slot, TradeService service) {
    Inventory inventory = session.inventoryFor(player.getUniqueId());
    ItemStack offered = inventory.getItem(slot);
    if (offered == null || offered.getType() == Material.AIR) {
      return;
    }
    inventory.setItem(slot, null);
    give(player, List.of(offered.clone()));
    service.offerChanged(player.getUniqueId());
    refresh(session, service);
  }

  private static void confirm(Player player, Session session, TradeService service) {
    TradeSnapshot snapshot = service.tradeOf(session.firstPlayerId()).orElse(null);
    if (snapshot == null) {
      cancel(session, "Trade is no longer active.");
      return;
    }
    TradeResult result =
        service.confirm(player.getUniqueId(), snapshot.offerVersionOf(player.getUniqueId()));
    if (result != TradeResult.SUCCESS) {
      player.sendMessage(ChatColor.RED + describe(result));
      return;
    }
    refresh(session, service);
    TradeSnapshot confirmed = service.tradeOf(session.firstPlayerId()).orElse(null);
    if (confirmed != null
        && confirmed.confirmed(session.firstPlayerId())
        && confirmed.confirmed(session.secondPlayerId())) {
      complete(session, service, confirmed);
    }
  }

  private static void complete(Session session, TradeService service, TradeSnapshot snapshot) {
    Player first = Bukkit.getPlayer(session.firstPlayerId());
    Player second = Bukkit.getPlayer(session.secondPlayerId());
    if (first == null || second == null) {
      cancel(session, "Trade cancelled because a player left.");
      return;
    }
    List<ItemStack> firstOffer = offerItems(session.inventoryFor(first.getUniqueId()), 0);
    List<ItemStack> secondOffer = offerItems(session.inventoryFor(second.getUniqueId()), 1);
    TradeResult result =
        service.complete(
            first.getUniqueId(),
            snapshot.offerVersionOf(first.getUniqueId()),
            snapshot.offerVersionOf(second.getUniqueId()));
    if (result != TradeResult.SUCCESS) {
      first.sendMessage(ChatColor.RED + describe(result));
      return;
    }
    removeOffer(session.inventoryFor(first.getUniqueId()), 0);
    removeOffer(session.inventoryFor(second.getUniqueId()), 1);
    give(first, secondOffer);
    give(second, firstOffer);
    SESSIONS.remove(session.firstPlayerId());
    SESSIONS.remove(session.secondPlayerId());
    SESSIONS.remove(snapshot.tradeId());
    first.closeInventory();
    second.closeInventory();
    first.sendMessage(ChatColor.GREEN + "Trade complete.");
    second.sendMessage(ChatColor.GREEN + "Trade complete.");
  }

  private static List<ItemStack> offerItems(Inventory inventory, int viewer) {
    List<ItemStack> items = new ArrayList<>();
    int start = viewer == 0 ? FIRST_OFFER_START : SECOND_OFFER_START;
    int end = viewer == 0 ? FIRST_OFFER_END : SECOND_OFFER_END;
    for (int slot = start; slot <= end; slot++) {
      ItemStack item = inventory.getItem(slot);
      if (item != null && item.getType() != Material.AIR) {
        items.add(item.clone());
      }
    }
    return items;
  }

  private static void removeOffer(Inventory inventory, int viewer) {
    int start = viewer == 0 ? FIRST_OFFER_START : SECOND_OFFER_START;
    int end = viewer == 0 ? FIRST_OFFER_END : SECOND_OFFER_END;
    for (int slot = start; slot <= end; slot++) {
      inventory.setItem(slot, null);
    }
  }

  private static void give(Player recipient, List<ItemStack> items) {
    for (ItemStack item : items) {
      for (ItemStack leftover : recipient.getInventory().addItem(item).values()) {
        recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover);
      }
    }
  }

  private static void refresh(Session session, TradeService service) {
    TradeSnapshot snapshot = service.tradeOf(session.firstPlayerId()).orElse(null);
    if (snapshot == null) {
      return;
    }
    setConfirmation(
        session.firstInventory(), FIRST_CONFIRM_SLOT, snapshot.confirmed(session.firstPlayerId()));
    setConfirmation(
        session.secondInventory(),
        SECOND_CONFIRM_SLOT,
        snapshot.confirmed(session.secondPlayerId()));
    syncMirroredOffer(session);
  }

  private static void syncMirroredOffer(Session session) {
    copyRegion(
        session.firstInventory(),
        session.secondInventory(),
        FIRST_OFFER_START,
        FIRST_OFFER_END,
        SECOND_OFFER_START);
    copyRegion(
        session.secondInventory(),
        session.firstInventory(),
        SECOND_OFFER_START,
        SECOND_OFFER_END,
        FIRST_OFFER_START);
  }

  private static void copyRegion(
      Inventory source, Inventory target, int start, int end, int targetStart) {
    for (int slot = start; slot <= end; slot++) {
      ItemStack item = source.getItem(slot);
      target.setItem(targetStart + slot - start, item == null ? null : item.clone());
    }
  }

  private static void setConfirmation(Inventory inventory, int slot, boolean confirmed) {
    inventory.setItem(
        slot,
        control(
            confirmed ? Material.GREEN_WOOL : Material.LIME_DYE,
            confirmed ? ChatColor.GREEN + "Confirmed" : ChatColor.GREEN + "Confirm your offer"));
  }

  private static void cancel(Session session, String message) {
    cancel(session, message, null);
  }

  private static void cancel(Session session, String message, Player departingPlayer) {
    Player first = playerFor(session.firstPlayerId(), departingPlayer);
    Player second = playerFor(session.secondPlayerId(), departingPlayer);
    returnOffer(first, session.firstPlayerId(), session.firstInventory(), 0);
    returnOffer(second, session.secondPlayerId(), session.secondInventory(), 1);
    session.service().cancel(session.firstPlayerId());
    SESSIONS.remove(session.firstPlayerId());
    SESSIONS.remove(session.secondPlayerId());
    notifyCancellation(first, message);
    notifyCancellation(second, message);
  }

  private static Player playerFor(UUID playerId, Player departingPlayer) {
    return departingPlayer != null && departingPlayer.getUniqueId().equals(playerId)
        ? departingPlayer
        : Bukkit.getPlayer(playerId);
  }

  private static void notifyCancellation(Player player, String message) {
    if (player != null && player.isOnline()) {
      player.closeInventory();
      player.sendMessage(ChatColor.RED + message);
    }
  }

  private static void returnOffer(Player player, UUID ownerId, Inventory inventory, int viewer) {
    List<ItemStack> items = offerItems(inventory, viewer);
    removeOffer(inventory, viewer);
    if (player == null) {
      PENDING_RETURNS
          .computeIfAbsent(ownerId, ignored -> new CopyOnWriteArrayList<>())
          .addAll(items);
      return;
    }
    give(player, items);
  }

  private static void deliverPendingReturns(Player player) {
    List<ItemStack> items = PENDING_RETURNS.remove(player.getUniqueId());
    if (items != null) {
      give(player, items);
    }
  }

  private static String describe(TradeResult result) {
    return switch (result) {
      case STALE_OFFER -> "The offer changed; confirm again.";
      case NOT_CONFIRMED -> "Both players must confirm first.";
      case ALREADY_CONFIRMED -> "You already confirmed this offer.";
      case NOT_PARTICIPANT -> "You are not in an active trade.";
      case ALREADY_COMPLETED -> "This trade is no longer active.";
      default -> "The trade could not be completed.";
    };
  }

  private static final class TradeListener implements Listener {
    private final TradeService service;

    private TradeListener(TradeService service) {
      this.service = service;
    }

    @EventHandler
    void onInventoryClick(InventoryClickEvent event) {
      handleClick(event, service);
    }

    @EventHandler
    void onInventoryDrag(InventoryDragEvent event) {
      if (isTradeTitle(event.getView().getTitle())) {
        event.setCancelled(true);
      }
    }

    @EventHandler
    void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)
          || !isTradeTitle(event.getView().getTitle())) {
        return;
      }
      Session session = SESSIONS.get(player.getUniqueId());
      if (session != null) {
        cancel(session, "Trade cancelled.");
      }
    }

    @EventHandler
    void onPlayerJoin(PlayerJoinEvent event) {
      deliverPendingReturns(event.getPlayer());
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
      Session session = SESSIONS.get(event.getPlayer().getUniqueId());
      if (session != null) {
        cancel(session, "Trade cancelled because a player left.", event.getPlayer());
      }
    }
  }

  private record Session(
      TradeService service,
      UUID firstPlayerId,
      UUID secondPlayerId,
      Inventory firstInventory,
      Inventory secondInventory) {
    private int viewerIndex(UUID playerId) {
      return firstPlayerId.equals(playerId) ? 0 : 1;
    }

    private int confirmSlot(int viewer) {
      return viewer == 0 ? FIRST_CONFIRM_SLOT : SECOND_CONFIRM_SLOT;
    }

    private Inventory inventoryFor(UUID playerId) {
      return firstPlayerId.equals(playerId) ? firstInventory : secondInventory;
    }
  }
}
