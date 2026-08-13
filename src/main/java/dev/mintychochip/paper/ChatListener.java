package dev.mintychochip.paper;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import dev.mintychochip.api.ChatMessage;
import dev.mintychochip.api.ChatService;
import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyService;
import dev.mintychochip.api.TitleService;
import dev.mintychochip.core.ChatRouter;
import dev.mintychochip.core.PresenceSnapshot;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Folia-safe Paper adapter for channel routing and rendering. */
public final class ChatListener implements Listener {
  private static final long CONTEXT_TIMEOUT_MILLIS = 250;
  private final java.util.concurrent.ConcurrentMap<UUID, OneShotSelection> transientChannels =
      new ConcurrentHashMap<>();
  private final Plugin plugin;
  private final ChatService chatService;
  private final PartyService partyService;
  private final TitleService titleService;
  private final ChatRouter router;
  private final ChatPresenceRegistry registry;
  private final java.util.Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
  private final Object lifecycleLock = new Object();
  private int activeHandlers;
  private volatile boolean closed;

  public ChatListener(
      Plugin plugin,
      ChatService chatService,
      PartyService partyService,
      TitleService titleService,
      ChatRouter router,
      ChatPresenceRegistry registry) {
    this.plugin = plugin;
    this.chatService = chatService;
    this.partyService = partyService;
    this.titleService = titleService;
    this.router = router;
    this.registry = registry;
  }

  public void sendOnce(Player player, ChannelId channel, String message) {
    UUID playerId = player.getUniqueId();
    OneShotSelection selection =
        new OneShotSelection(channel, message, Instant.now().plusSeconds(5));
    transientChannels.put(playerId, selection);
    Bukkit.getGlobalRegionScheduler()
        .runDelayed(plugin, ignored -> transientChannels.remove(playerId, selection), 100L);
    player.chat(message);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    update(event.getPlayer().getUniqueId(), event.getPlayer().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    registry.remove(id);
    ChatCooldowns.clear(id);
    transientChannels.remove(id);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    if (event.hasChangedBlock() || event.hasChangedOrientation())
      update(event.getPlayer().getUniqueId(), event.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    update(event.getPlayer().getUniqueId(), event.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldChange(PlayerChangedWorldEvent event) {
    update(event.getPlayer().getUniqueId(), event.getPlayer().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    update(event.getPlayer().getUniqueId(), event.getRespawnLocation());
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onChat(AsyncChatEvent event) {
    if (!beginHandler()) {
      event.setCancelled(true);
      return;
    }
    try {
      handleChat(event);
    } finally {
      endHandler();
    }
  }

  private void handleChat(AsyncChatEvent event) {
    Player sender = event.getPlayer();
    UUID senderId = sender.getUniqueId();
    String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
    ChannelPreferences preferences = chatService.preferences(senderId);
    Instant now = Instant.now();
    ChannelId channel =
        consumeSelection(transientChannels, senderId, plain, now)
            .orElse(preferences.activeChannel());
    if (!sender.hasPermission("extras.chat.use")
        || !sender.hasPermission(ChatCommand.permission(channel))) {
      reject(event, sender, "You do not have permission to use that channel.");
      return;
    }
    final ChatMessage message;
    try {
      message = new ChatMessage(senderId, channel, plain, now);
    } catch (IllegalArgumentException rejected) {
      reject(event, sender, rejected.getMessage());
      return;
    }
    ItemLinkParser.Kind itemKind = ItemLinkParser.parse(plain).kind();
    if (itemKind == ItemLinkParser.Kind.TOO_MANY_TOKENS) {
      reject(event, sender, "Only one %item link is allowed per message.");
      return;
    }
    Optional<PresenceSnapshot> senderPresence = registry.snapshot(senderId);
    if (senderPresence.isEmpty()) {
      reject(event, sender, "Your chat presence is not ready yet.");
      return;
    }
    Optional<CapturedContext> captured = captureContext(event, sender, channel, itemKind);
    if (captured.isEmpty()) {
      reject(event, sender, "Could not safely capture your chat context; try again.");
      return;
    }
    CapturedContext context = captured.get();
    if (itemKind == ItemLinkParser.Kind.ONE_TOKEN
        && (context.heldItem() == null || context.heldItem().getType() == Material.AIR)) {
      reject(event, sender, "Hold an item in your main hand to use %item.");
      return;
    }
    var delivery =
        router.route(
            message,
            senderPresence.get(),
            registry.snapshots().values(),
            context.party(),
            chatService::preferences);
    if (!delivery.accepted()) {
      reject(event, sender, "You must be in a party to use party chat.");
      return;
    }
    java.time.Duration cooldown = ChatCooldowns.admit(senderId, channel, now);
    if (!cooldown.isZero()) {
      reject(
          event,
          sender,
          "Wait "
              + String.format(java.util.Locale.ROOT, "%.1f", cooldown.toMillis() / 1000.0)
              + "s before chatting again.");
      return;
    }
    event
        .viewers()
        .removeIf(
            audience ->
                !(audience instanceof Player viewer)
                    || !delivery.recipients().contains(viewer.getUniqueId()));
    ChannelId renderedChannel = channel;
    event.renderer(
        ChatRenderer.viewerUnaware(
            (source, displayName, renderedMessage) ->
                ChatFormatter.format(
                    renderedChannel,
                    context.title(),
                    displayName,
                    renderedMessage,
                    context.heldItem())));
  }

  private Optional<CapturedContext> captureContext(
      AsyncChatEvent event, Player player, ChannelId channel, ItemLinkParser.Kind itemKind) {
    if (!event.isAsynchronous()) return Optional.of(captureDirect(player, channel, itemKind));
    CompletableFuture<CapturedContext> future = new CompletableFuture<>();
    pending.add(future);
    boolean scheduled =
        player
            .getScheduler()
            .execute(
                plugin,
                () -> {
                  if (!closed) future.complete(captureDirect(player, channel, itemKind));
                },
                () -> future.complete(null),
                1L);
    if (!scheduled) return Optional.empty();
    try {
      return Optional.ofNullable(future.get(CONTEXT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    } catch (Exception rejected) {
      return Optional.empty();
    } finally {
      pending.remove(future);
    }
  }

  private CapturedContext captureDirect(
      Player player, ChannelId channel, ItemLinkParser.Kind itemKind) {
    ItemStack heldItem = null;
    if (itemKind == ItemLinkParser.Kind.ONE_TOKEN) {
      ItemStack current = player.getInventory().getItemInMainHand();
      heldItem = current == null ? null : current.clone();
    }
    Optional<Party> party =
        channel == ChannelId.PARTY ? partyService.partyOf(player.getUniqueId()) : Optional.empty();
    Component title =
        titleService
            .equippedTitle(player.getUniqueId())
            .map(Component::text)
            .orElse(Component.empty());
    return new CapturedContext(heldItem, party, title);
  }

  private void reject(AsyncChatEvent event, Player sender, String message) {
    event.setCancelled(true);
    if (!event.isAsynchronous()) {
      sender.sendMessage(Component.text(message));
      return;
    }
    if (!closed)
      sender
          .getScheduler()
          .execute(
              plugin,
              () -> {
                if (!closed) sender.sendMessage(Component.text(message));
              },
              null,
              1L);
  }

  public void close() {
    synchronized (lifecycleLock) {
      closed = true;
    }
    pending.forEach(future -> future.cancel(false));
    synchronized (lifecycleLock) {
      boolean interrupted = false;
      while (activeHandlers > 0) {
        try {
          lifecycleLock.wait();
        } catch (InterruptedException interruptedException) {
          interrupted = true;
        }
      }
      if (interrupted) Thread.currentThread().interrupt();
    }
    pending.clear();
    transientChannels.clear();
  }

  private boolean beginHandler() {
    synchronized (lifecycleLock) {
      if (closed) return false;
      activeHandlers++;
      return true;
    }
  }

  private void endHandler() {
    synchronized (lifecycleLock) {
      activeHandlers--;
      if (activeHandlers == 0) lifecycleLock.notifyAll();
    }
  }

  private void update(UUID id, Location location) {
    if (!closed && location != null && location.getWorld() != null)
      registry.update(
          new PresenceSnapshot(
              id, location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()));
  }

  static Optional<ChannelId> consumeSelection(
      java.util.concurrent.ConcurrentMap<UUID, OneShotSelection> selections,
      UUID playerId,
      String message,
      Instant now) {
    java.util.concurrent.atomic.AtomicReference<ChannelId> consumed =
        new java.util.concurrent.atomic.AtomicReference<>();
    selections.compute(
        playerId,
        (ignored, selection) -> {
          if (selection == null) return null;
          if (selection.matches(message, now)) {
            consumed.set(selection.channel());
            return null;
          }
          return now.isAfter(selection.expiredAt()) ? null : selection;
        });
    return Optional.ofNullable(consumed.get());
  }

  record OneShotSelection(ChannelId channel, String message, Instant expiredAt) {
    boolean matches(String candidate, Instant now) {
      return !now.isAfter(expiredAt) && message.equals(candidate);
    }
  }

  private record CapturedContext(ItemStack heldItem, Optional<Party> party, Component title) {}
}
