package dev.mintychochip.paper;

import dev.mintychochip.api.AdvancementToastRequest;
import dev.mintychochip.api.AdvancementToastSender;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper implementation of the advancement toast API.
 *
 * <p>Maps {@link AdvancementToastRequest} through {@link PaperAdvancementToastDisplay} and sends it
 * with a temporary advancement that has {@code show_toast} (grant then revoke). There is no {@code
 * Player.sendToast} on Paper 1.21.11.
 */
public final class PaperAdvancementToastSender implements AdvancementToastSender {

  /** Delivers a mapped Paper toast display. Tests inject a recorder; production uses Bukkit. */
  @FunctionalInterface
  public interface Transport {
    void send(PaperAdvancementToastDisplay display);
  }

  private final Transport transport;

  public PaperAdvancementToastSender(JavaPlugin plugin) {
    this(new BukkitAdvancementToastTransport(plugin));
  }

  public PaperAdvancementToastSender(Transport transport) {
    this.transport = Objects.requireNonNull(transport, "transport");
  }

  @Override
  public void send(AdvancementToastRequest request) {
    transport.send(PaperAdvancementToastDisplay.from(request));
  }

  /** Temporary load/grant/revoke of an advancement so the client shows the toast. */
  static final class BukkitAdvancementToastTransport implements Transport {

    private static final String CRITERION = "impossible";

    private final JavaPlugin plugin;

    BukkitAdvancementToastTransport(JavaPlugin plugin) {
      this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void send(PaperAdvancementToastDisplay display) {
      Player player = Bukkit.getPlayer(display.playerId());
      if (player == null || !player.isOnline()) {
        return;
      }
      player.getScheduler().execute(plugin, () -> showToast(player, display), null, 1L);
    }

    private void showToast(Player player, PaperAdvancementToastDisplay display) {
      NamespacedKey key =
          new NamespacedKey(plugin, "toast_" + UUID.randomUUID().toString().replace("-", ""));
      Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, display.advancementJson());
      if (advancement == null) {
        advancement = Bukkit.getAdvancement(key);
      }
      if (advancement == null) {
        plugin.getLogger().warning("Failed to load temporary advancement toast " + key);
        return;
      }
      AdvancementProgress progress = player.getAdvancementProgress(advancement);
      progress.awardCriteria(CRITERION);
      Advancement awarded = advancement;
      player
          .getScheduler()
          .execute(
              plugin,
              () -> {
                player.getAdvancementProgress(awarded).revokeCriteria(CRITERION);
                Bukkit.getUnsafe().removeAdvancement(key);
              },
              null,
              2L);
    }
  }
}
