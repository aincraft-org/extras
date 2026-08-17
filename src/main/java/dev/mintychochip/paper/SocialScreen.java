package dev.mintychochip.paper;

import static de.flog99.mapgui.ui.Ui.Text;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Node;
import dev.mintychochip.api.FriendService;
import dev.mintychochip.api.PartyService;
import net.kyori.adventure.text.Component;

/** MapGUI screen for the social menu. */
final class SocialScreen extends Screen {

  private final FriendService friendService;
  private final PartyService partyService;

  SocialScreen(FriendService friendService, PartyService partyService, SocialTab initial) {
    this.friendService = friendService;
    this.partyService = partyService;
  }

  @Override
  public Component title() {
    return Component.text("Social");
  }

  @Override
  public HandOptions hand() {
    return HandOptions.popup();
  }

  @Override
  public Boolean clampPitch() {
    return false;
  }

  @Override
  public Click activateOn() {
    return Click.RIGHT;
  }

  @Override
  protected void onOpen() {}

  @Override
  protected Node build() {
    return Text("Loading...").color(java.awt.Color.WHITE);
  }
}
