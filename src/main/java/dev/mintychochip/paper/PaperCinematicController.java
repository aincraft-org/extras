package dev.mintychochip.paper;

import dev.mintychochip.api.cinematic.CameraPose;
import dev.mintychochip.api.cinematic.CinematicResult;
import dev.mintychochip.api.cinematic.CinematicService;
import dev.mintychochip.api.cinematic.PlaybackSnapshot;
import dev.mintychochip.api.cinematic.PropCue;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Folia-safe Paper adapter: applies sampled camera poses, vanilla shader overlays, and display
 * props for an active cinematic session.
 */
public final class PaperCinematicController implements Listener {

  private static final long TICK_PERIOD = 1L;
  private static final int OVERLAY_EFFECT_TICKS = 40;

  private final Plugin plugin;
  private final CinematicService cinematicService;
  private final ConcurrentMap<UUID, ScheduledTask> tickers = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AppliedState> applied = new ConcurrentHashMap<>();

  public PaperCinematicController(Plugin plugin, CinematicService cinematicService) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.cinematicService = Objects.requireNonNull(cinematicService, "cinematicService");
  }

  public CinematicResult play(Player player, String sceneName) {
    Objects.requireNonNull(player, "player");
    CinematicResult result =
        cinematicService.play(player.getUniqueId(), sceneName, poseOf(player.getLocation()));
    if (result != CinematicResult.SUCCESS) {
      return result;
    }
    startTicker(player);
    return CinematicResult.SUCCESS;
  }

  public Optional<PlaybackSnapshot> stop(Player player) {
    Objects.requireNonNull(player, "player");
    cancelTicker(player.getUniqueId());
    Optional<PlaybackSnapshot> stopped = cinematicService.stop(player.getUniqueId());
    stopped.ifPresent(snapshot -> apply(player, snapshot));
    return stopped;
  }

  public void close() {
    for (UUID playerId : List.copyOf(tickers.keySet())) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        stop(player);
      } else {
        cancelTicker(playerId);
        cinematicService.stop(playerId);
        AppliedState state = applied.remove(playerId);
        if (state != null) {
          state.removeProps();
        }
      }
    }
    tickers.clear();
    applied.clear();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    cancelTicker(player.getUniqueId());
    cinematicService.stop(player.getUniqueId());
    AppliedState state = applied.remove(player.getUniqueId());
    if (state != null) {
      state.removeProps();
    }
  }

  static CameraPose poseOf(Location location) {
    World world = location.getWorld();
    String worldIdentity = world == null ? "world" : world.getUID().toString();
    return new CameraPose(
        worldIdentity,
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch());
  }

  static Location locationOf(CameraPose pose) {
    World world = worldOf(pose.worldIdentity());
    if (world == null) {
      return null;
    }
    return new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
  }

  private static World worldOf(String worldIdentity) {
    World byId;
    try {
      byId = Bukkit.getWorld(UUID.fromString(worldIdentity));
    } catch (IllegalArgumentException notAUuid) {
      byId = null;
    }
    if (byId != null) {
      return byId;
    }
    return Bukkit.getWorld(worldIdentity);
  }

  private void startTicker(Player player) {
    UUID playerId = player.getUniqueId();
    cancelTicker(playerId);
    long startedNanos = System.nanoTime();
    ScheduledTask task =
        player
            .getScheduler()
            .runAtFixedRate(
                plugin,
                scheduled -> {
                  double elapsed = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
                  Optional<PlaybackSnapshot> frame =
                      cinematicService.samplePlayback(playerId, elapsed);
                  if (frame.isEmpty() || !frame.get().playing()) {
                    frame.ifPresent(snapshot -> apply(player, snapshot));
                    applied.remove(playerId);
                    scheduled.cancel();
                    tickers.remove(playerId, scheduled);
                    return;
                  }
                  apply(player, frame.get());
                },
                null,
                TICK_PERIOD,
                TICK_PERIOD);
    if (task != null) {
      tickers.put(playerId, task);
    }
  }

  private void cancelTicker(UUID playerId) {
    ScheduledTask task = tickers.remove(playerId);
    if (task != null) {
      task.cancel();
    }
  }

  private void apply(Player player, PlaybackSnapshot snapshot) {
    Location location = locationOf(snapshot.pose());
    if (location != null) {
      player.teleport(location);
    }
    AppliedState state = applied.computeIfAbsent(player.getUniqueId(), id -> new AppliedState());
    state.syncShaders(player, snapshot.shaders());
    state.syncProps(plugin, player, snapshot.props());
    if (!snapshot.playing()) {
      state.clear(player);
      applied.remove(player.getUniqueId());
    }
  }

  private static final class AppliedState {
    private final Set<String> shaders = new LinkedHashSet<>();
    private final Map<PropCue, Entity> props = new LinkedHashMap<>();

    private void syncShaders(Player player, List<String> active) {
      Set<String> wanted = new LinkedHashSet<>(active);
      for (String overlayId : List.copyOf(shaders)) {
        if (!wanted.contains(overlayId)) {
          removeOverlay(player, overlayId);
          shaders.remove(overlayId);
        }
      }
      for (String overlayId : wanted) {
        applyOverlay(player, overlayId);
        shaders.add(overlayId);
      }
    }

    private void syncProps(Plugin plugin, Player player, List<PropCue> active) {
      Set<PropCue> wanted = new LinkedHashSet<>(active);
      for (PropCue cue : List.copyOf(props.keySet())) {
        if (!wanted.contains(cue)) {
          removeProp(props.remove(cue));
        }
      }
      for (PropCue cue : wanted) {
        if (!props.containsKey(cue)) {
          Entity spawned = spawnProp(plugin, player, cue);
          if (spawned != null) {
            props.put(cue, spawned);
          }
        }
      }
    }

    private void clear(Player player) {
      for (String overlayId : List.copyOf(shaders)) {
        removeOverlay(player, overlayId);
      }
      shaders.clear();
      removeProps();
    }

    private void removeProps() {
      for (Entity entity : new ArrayList<>(props.values())) {
        removeProp(entity);
      }
      props.clear();
    }
  }

  private static void applyOverlay(Player player, String overlayId) {
    PotionEffectType type = effectType(overlayId);
    if (type == null) {
      return;
    }
    player.addPotionEffect(new PotionEffect(type, OVERLAY_EFFECT_TICKS, 0, true, false, false));
  }

  private static void removeOverlay(Player player, String overlayId) {
    PotionEffectType type = effectType(overlayId);
    if (type != null) {
      player.removePotionEffect(type);
    }
  }

  private static PotionEffectType effectType(String overlayId) {
    Optional<String> key = VanillaShaderOverlays.potionEffectKey(overlayId);
    if (key.isEmpty()) {
      return null;
    }
    NamespacedKey namespaced = NamespacedKey.fromString(key.get());
    if (namespaced == null) {
      return null;
    }
    return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(namespaced);
  }

  private static Entity spawnProp(Plugin plugin, Player player, PropCue cue) {
    Location location = locationOf(cue.pose());
    if (location == null || location.getWorld() == null) {
      return null;
    }
    Material material = Material.matchMaterial(cue.propId());
    Display display;
    if (material != null && material.isBlock()) {
      display =
          location
              .getWorld()
              .spawn(
                  location,
                  BlockDisplay.class,
                  entity -> entity.setBlock(material.createBlockData()));
    } else {
      Material item = material == null ? Material.ARMOR_STAND : material;
      display =
          location
              .getWorld()
              .spawn(
                  location, ItemDisplay.class, entity -> entity.setItemStack(new ItemStack(item)));
    }
    display.setPersistent(false);
    display.setGravity(false);
    display.setInvulnerable(true);
    display.setVisibleByDefault(false);
    player.showEntity(plugin, display);
    return display;
  }

  private static void removeProp(Entity entity) {
    if (entity != null && entity.isValid()) {
      entity.remove();
    }
  }
}
