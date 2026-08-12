package dev.mintychochip.paper;

import dev.mintychochip.api.PartyService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Presence integration: loads party state on login (async-safe) and announces join/quit to other
 * online party members.
 *
 * <p>Adapter guidance: announcements fire from {@link PlayerJoinEvent} / {@link PlayerQuitEvent}
 * only — {@code AsyncPlayerPreLoginEvent} runs on an async thread where the joining {@link Player}
 * does not exist yet. Only thread-safe state loading happens there.
 */
public final class PartyLifecycleListener implements Listener {

  private final PartyService partyService;

  public PartyLifecycleListener(PartyService partyService) {
    this.partyService = partyService;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }
    // Warm the party cache; no announcements (async, player not available).
    partyService.partyOf(event.getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    partyService
        .partyOf(playerId)
        .ifPresent(
            party -> {
              String name = event.getPlayer().getName();
              for (UUID memberId : party.memberIds()) {
                if (memberId.equals(playerId)) {
                  continue;
                }
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                  member.sendMessage(name + " is now online.");
                }
              }
            });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    String name = event.getPlayer().getName();
    // Leader auto-transfer (or cache eviction for members) happens first so
    // the announcement reflects the post-logout party state.
    if (partyService instanceof dev.mintychochip.core.DefaultPartyService service) {
      service.logout(playerId);
    }
    partyService
        .partyOf(playerId)
        .ifPresent(
            party -> {
              for (UUID memberId : party.memberIds()) {
                if (memberId.equals(playerId)) {
                  continue;
                }
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                  member.sendMessage(name + " is now offline.");
                }
              }
            });
  }
}
