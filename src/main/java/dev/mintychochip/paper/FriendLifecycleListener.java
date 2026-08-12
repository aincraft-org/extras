package dev.mintychochip.paper;

import dev.mintychochip.api.FriendService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Friend presence integration: announces login/logout to online friends.
 *
 * <p>Announcements fire from {@link PlayerJoinEvent} and {@link PlayerQuitEvent} only; the friend
 * store does not need pre-login cache warming, so no async pre-login hook is present.
 */
public final class FriendLifecycleListener implements Listener {

  private final FriendService friendService;

  public FriendLifecycleListener(FriendService friendService) {
    this.friendService = friendService;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    String name = event.getPlayer().getName();
    announce(playerId, name, " is now online.");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    String name = event.getPlayer().getName();
    announce(playerId, name, " is now offline.");
  }

  private void announce(UUID playerId, String name, String suffix) {
    for (UUID friendId : friendService.friendIdsOf(playerId)) {
      Player friend = Bukkit.getPlayer(friendId);
      if (friend != null && friend.isOnline()) {
        friend.sendMessage(name + suffix);
      }
    }
  }
}
