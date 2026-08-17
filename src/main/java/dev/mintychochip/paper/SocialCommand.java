package dev.mintychochip.paper;

import dev.mintychochip.api.FriendService;
import dev.mintychochip.api.PartyService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /social} — open the MapGUI social menu. */
public final class SocialCommand implements BasicCommand {

  private final FriendService friendService;
  private final PartyService partyService;

  public SocialCommand(FriendService friendService, PartyService partyService) {
    this.friendService = friendService;
    this.partyService = partyService;
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Only players can open the social menu."));
      return;
    }
    openMenu(player, defaultTab(player.getUniqueId()));
  }

  private SocialTab defaultTab(UUID playerId) {
    return partyService.partyOf(playerId).isPresent() ? SocialTab.PARTY : SocialTab.FRIENDS;
  }

  private void openMenu(Player player, SocialTab tab) {
    var mapGui = de.flog99.mapgui.MapGui.get();
    if (mapGui == null) {
      player.sendMessage(Component.text("The social menu is unavailable."));
      return;
    }
    mapGui.open(player, new SocialScreen(friendService, partyService, tab));
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    return List.of();
  }
}
