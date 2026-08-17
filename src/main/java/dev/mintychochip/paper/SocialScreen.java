package dev.mintychochip.paper;

import static de.flog99.mapgui.ui.Ui.*;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.State;
import dev.mintychochip.api.FriendRequest;
import dev.mintychochip.api.FriendResult;
import dev.mintychochip.api.FriendService;
import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;
import dev.mintychochip.api.PartyResult;
import dev.mintychochip.api.PartyService;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** MapGUI social menu with friends, party, invites, and a player picker. */
final class SocialScreen extends Screen {

  private final FriendService friendService;
  private final PartyService partyService;

  private final State<SocialTab> activeTab;
  private final State<SocialTab> previousTab;
  private final State<PlayerPickerMode> pickerMode;
  private final State<String> newPartyName;

  private List<UUID> friends = List.of();
  private List<FriendRequest> incomingRequests = List.of();
  private List<FriendRequest> outgoingRequests = List.of();
  private List<PartyInvite> pendingPartyInvites = List.of();
  private Optional<Party> currentParty = Optional.empty();

  SocialScreen(FriendService friendService, PartyService partyService, SocialTab initial) {
    this.friendService = Objects.requireNonNull(friendService, "friendService");
    this.partyService = Objects.requireNonNull(partyService, "partyService");
    this.activeTab = state(initial);
    this.previousTab = state(initial);
    this.pickerMode = state((PlayerPickerMode) null);
    this.newPartyName = state("");
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
  protected void onOpen() {
    refresh();
  }

  private void refresh() {
    Player self = player();
    UUID selfId = self.getUniqueId();
    friends = friendService.friendIdsOf(selfId);
    incomingRequests = friendService.incomingRequests(selfId);
    outgoingRequests = friendService.outgoingRequests(selfId);
    pendingPartyInvites = partyService.pendingInvitations(selfId);
    currentParty = partyService.partyOf(selfId);
  }

  @Override
  protected Node build() {
    return Column(topBar(), body()).gap(2).padding(2).fill();
  }

  private Node topBar() {
    if (pickerMode.get() != null) {
      return Row(
              Button("< Back")
                  .onClick(this::closePicker)
                  .background(theme().surface())
                  .textColor(Color.WHITE),
              Spacer(),
              Text(pickerTitle()).color(Color.WHITE).shadow())
          .gap(2)
          .padding(2)
          .fillWidth();
    }
    return Row(
            tabButton("Friends", SocialTab.FRIENDS),
            tabButton("Party", SocialTab.PARTY),
            tabButton("Invites", SocialTab.INVITES))
        .gap(2)
        .padding(2)
        .fillWidth();
  }

  private Node tabButton(String label, SocialTab tab) {
    boolean active = activeTab.get() == tab;
    return Button(label)
        .onClick(() -> activeTab.set(tab))
        .background(active ? theme().accent() : theme().surface())
        .textColor(active ? Color.WHITE : theme().muted())
        .fill();
  }

  private String pickerTitle() {
    return pickerMode.get() == PlayerPickerMode.ADD_FRIEND ? "Add Friend" : "Invite to Party";
  }

  private void closePicker() {
    pickerMode.set(null);
    activeTab.set(previousTab.get());
  }

  private Node body() {
    return switch (activeTab.get()) {
      case FRIENDS -> buildFriends();
      case PARTY -> buildParty();
      case INVITES -> buildInvites();
      case PICKER -> buildPicker();
    };
  }

  private Node buildFriends() {
    List<Node> nodes = new ArrayList<>();
    nodes.add(
        Row(
                Text("Friends").color(Color.WHITE).shadow(),
                Spacer(),
                Button("+ Add")
                    .onClick(this::openAddFriend)
                    .background(theme().accent())
                    .textColor(Color.WHITE))
            .gap(2)
            .fillWidth());
    if (friends.isEmpty()) {
      nodes.add(Text("No friends yet.").color(theme().muted()));
    } else {
      nodes.addAll(each(friends, UUID::toString, this::friendRow));
    }
    if (!incomingRequests.isEmpty() || !outgoingRequests.isEmpty()) {
      nodes.add(Divider(theme().muted()));
      nodes.add(Text("Requests").color(Color.WHITE).shadow());
      if (!incomingRequests.isEmpty()) {
        nodes.addAll(
            each(incomingRequests, r -> r.requesterId().toString(), this::incomingFriendRow));
      }
      if (!outgoingRequests.isEmpty()) {
        nodes.addAll(each(outgoingRequests, r -> r.targetId().toString(), this::outgoingFriendRow));
      }
    }
    return Scroll(nodes).gap(2).padding(2).fill();
  }

  private Node friendRow(UUID friendId) {
    String name = PlayerIds.playerName(friendId);
    Color nameColor = PlayerIds.isOnline(friendId) ? theme().success() : theme().muted();
    return Row(
            Text(name).color(nameColor),
            Spacer(),
            Button("Remove")
                .onClick(() -> removeFriend(friendId))
                .background(theme().danger())
                .textColor(Color.WHITE)
                .size(28, 9))
        .gap(2)
        .align(Align.CENTER)
        .fillWidth();
  }

  private Node incomingFriendRow(FriendRequest request) {
    String name = PlayerIds.playerName(request.requesterId());
    return Row(
            Text(name).color(Color.WHITE),
            Spacer(),
            Button("Accept")
                .onClick(() -> acceptFriend(request.requesterId()))
                .background(theme().accent())
                .textColor(Color.WHITE)
                .size(24, 9),
            Button("Decline")
                .onClick(() -> declineFriend(request.requesterId()))
                .background(theme().danger())
                .textColor(Color.WHITE)
                .size(24, 9))
        .gap(1)
        .align(Align.CENTER)
        .fillWidth();
  }

  private Node outgoingFriendRow(FriendRequest request) {
    String name = PlayerIds.playerName(request.targetId());
    return Row(
            Text("> " + name).color(theme().muted()),
            Spacer(),
            Button("Cancel")
                .onClick(() -> cancelFriend(request.targetId()))
                .background(theme().muted())
                .textColor(Color.WHITE)
                .size(24, 9))
        .gap(1)
        .align(Align.CENTER)
        .fillWidth();
  }

  private Node buildInvites() {
    List<Node> nodes = new ArrayList<>();
    nodes.add(Text("Invites").color(Color.WHITE).shadow());
    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty() && pendingPartyInvites.isEmpty()) {
      nodes.add(Text("No pending invites.").color(theme().muted()));
    }
    if (!incomingRequests.isEmpty()) {
      nodes.add(Text("Friend requests:").color(Color.WHITE));
      nodes.addAll(
          each(incomingRequests, r -> r.requesterId().toString(), this::incomingFriendRow));
    }
    if (!outgoingRequests.isEmpty()) {
      nodes.add(Text("Outgoing:").color(Color.WHITE));
      nodes.addAll(each(outgoingRequests, r -> r.targetId().toString(), this::outgoingFriendRow));
    }
    if (!pendingPartyInvites.isEmpty()) {
      nodes.add(Text("Party invites:").color(Color.WHITE));
      nodes.addAll(each(pendingPartyInvites, i -> i.partyId().toString(), this::partyInviteRow));
    }
    return Scroll(nodes).gap(2).padding(2).fill();
  }

  private Node buildParty() {
    return currentParty.isEmpty() ? buildNoParty() : buildInParty(currentParty.get());
  }

  private Node buildNoParty() {
    List<Node> nodes = new ArrayList<>();
    nodes.add(Text("Create Party").color(Color.WHITE).shadow());
    nodes.add(
        Row(
                Field(newPartyName).placeholder("optional name").maxLength(32).fill(),
                Button("Create")
                    .onClick(this::createParty)
                    .background(theme().accent())
                    .textColor(Color.WHITE))
            .gap(2)
            .fillWidth());
    if (!pendingPartyInvites.isEmpty()) {
      nodes.add(Divider(theme().muted()));
      nodes.add(Text("Invites").color(Color.WHITE).shadow());
      nodes.addAll(each(pendingPartyInvites, i -> i.partyId().toString(), this::partyInviteRow));
    }
    return Scroll(nodes).gap(2).padding(2).fill();
  }

  private Node buildInParty(Party party) {
    List<Node> nodes = new ArrayList<>();
    UUID selfId = player().getUniqueId();
    boolean leader = party.isLeader(selfId);
    String partyName = party.name() != null ? party.name() : "Unnamed party";

    Node actionButton =
        leader
            ? Button("Disband")
                .onClick(this::disband)
                .background(theme().danger())
                .textColor(Color.WHITE)
                .size(28, 9)
            : Button("Leave")
                .onClick(this::leave)
                .background(theme().danger())
                .textColor(Color.WHITE)
                .size(24, 9);

    nodes.add(
        Row(
                Text(partyName).color(Color.WHITE).shadow(),
                Spacer(),
                Button("Invite")
                    .onClick(this::openInvite)
                    .background(theme().accent())
                    .textColor(Color.WHITE)
                    .size(24, 9),
                actionButton)
            .gap(1)
            .fillWidth());

    for (UUID memberId : party.memberIds()) {
      nodes.add(memberRow(party, memberId, leader));
    }
    return Scroll(nodes).gap(2).padding(2).fill();
  }

  private Node memberRow(Party party, UUID memberId, boolean viewerIsLeader) {
    String name = PlayerIds.playerName(memberId);
    boolean isSelf = memberId.equals(player().getUniqueId());
    boolean isLeader = party.isLeader(memberId);
    Color nameColor = isSelf ? theme().accent() : Color.WHITE;
    String label = (isLeader ? "* " : "") + name;
    if (viewerIsLeader && !isSelf) {
      return Row(
              Text(label).color(nameColor),
              Spacer(),
              Button("Kick")
                  .onClick(() -> kick(memberId))
                  .background(theme().danger())
                  .textColor(Color.WHITE)
                  .size(20, 9),
              Button("Transfer")
                  .onClick(() -> transfer(memberId))
                  .background(theme().accent())
                  .textColor(Color.WHITE)
                  .size(32, 9))
          .gap(1)
          .align(Align.CENTER)
          .fillWidth();
    }
    return Row(Text(label).color(nameColor), Spacer()).gap(1).fillWidth();
  }

  private Node partyInviteRow(PartyInvite invite) {
    return Row(
            Text("From " + PlayerIds.playerName(invite.inviterId())).color(Color.WHITE),
            Spacer(),
            Button("Accept")
                .onClick(() -> acceptParty(invite.partyId()))
                .background(theme().accent())
                .textColor(Color.WHITE)
                .size(24, 9),
            Button("Decline")
                .onClick(() -> declineParty(invite.partyId()))
                .background(theme().danger())
                .textColor(Color.WHITE)
                .size(24, 9))
        .gap(1)
        .align(Align.CENTER)
        .fillWidth();
  }

  private Node buildPicker() {
    Player self = player();
    UUID selfId = self.getUniqueId();
    List<Player> candidates = new ArrayList<>();
    for (Player candidate : Bukkit.getOnlinePlayers()) {
      if (candidate.getUniqueId().equals(selfId)) {
        continue;
      }
      if (pickable(selfId, candidate)) {
        candidates.add(candidate);
      }
    }
    if (candidates.isEmpty()) {
      return Scroll(Text("No players to pick from.").color(theme().muted()))
          .gap(2)
          .padding(2)
          .fill();
    }
    return Scroll(each(candidates, p -> p.getUniqueId().toString(), this::pickerRow))
        .gap(2)
        .padding(2)
        .fill();
  }

  private boolean pickable(UUID selfId, Player candidate) {
    UUID candidateId = candidate.getUniqueId();
    if (pickerMode.get() == PlayerPickerMode.ADD_FRIEND) {
      return !friends.contains(candidateId)
          && incomingRequests.stream().noneMatch(r -> r.requesterId().equals(candidateId))
          && outgoingRequests.stream().noneMatch(r -> r.targetId().equals(candidateId));
    }
    if (currentParty.isEmpty()) {
      return false;
    }
    UUID partyId = currentParty.get().partyId();
    if (!partyService.partyOf(candidateId).isEmpty()) {
      return false;
    }
    return partyService.pendingInvitations(candidateId).stream()
        .noneMatch(i -> i.partyId().equals(partyId));
  }

  private Node pickerRow(Player candidate) {
    String action = pickerMode.get() == PlayerPickerMode.ADD_FRIEND ? "Add" : "Invite";
    return Row(
            Text(candidate.getName()).color(Color.WHITE),
            Spacer(),
            Button(action)
                .onClick(() -> pickPlayer(candidate))
                .background(theme().accent())
                .textColor(Color.WHITE)
                .size(24, 9))
        .gap(1)
        .align(Align.CENTER)
        .fillWidth();
  }

  private void pickPlayer(Player candidate) {
    UUID selfId = player().getUniqueId();
    if (pickerMode.get() == PlayerPickerMode.ADD_FRIEND) {
      sendFriendRequest(candidate.getUniqueId());
    } else {
      inviteToParty(selfId, candidate.getUniqueId());
    }
    refresh();
    invalidate();
  }

  private void openAddFriend() {
    previousTab.set(activeTab.get());
    pickerMode.set(PlayerPickerMode.ADD_FRIEND);
    activeTab.set(SocialTab.PICKER);
    invalidate();
  }

  private void openInvite() {
    previousTab.set(activeTab.get());
    pickerMode.set(PlayerPickerMode.INVITE_TO_PARTY);
    activeTab.set(SocialTab.PICKER);
    invalidate();
  }

  private void createParty() {
    String raw = newPartyName.get().trim();
    String name = raw.isEmpty() ? null : raw;
    PartyResult result = partyService.createParty(player().getUniqueId(), name);
    if (result == PartyResult.SUCCESS) {
      hint("Party created", NamedTextColor.GREEN);
      newPartyName.set("");
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void disband() {
    PartyResult result = partyService.disband(player().getUniqueId());
    if (result == PartyResult.SUCCESS) {
      hint("Party disbanded", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void leave() {
    PartyResult result = partyService.leaveParty(player().getUniqueId());
    if (result == PartyResult.SUCCESS) {
      hint("Left party", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void kick(UUID targetId) {
    PartyResult result = partyService.kick(player().getUniqueId(), targetId);
    if (result == PartyResult.SUCCESS) {
      hint("Kicked " + PlayerIds.playerName(targetId), NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void transfer(UUID targetId) {
    PartyResult result = partyService.transferLeadership(player().getUniqueId(), targetId);
    if (result == PartyResult.SUCCESS) {
      hint("Transferred leadership", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void sendFriendRequest(UUID targetId) {
    FriendResult result = friendService.sendRequest(player().getUniqueId(), targetId);
    if (result == FriendResult.SUCCESS) {
      hint("Friend request sent", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
  }

  private void removeFriend(UUID targetId) {
    FriendResult result = friendService.removeFriend(player().getUniqueId(), targetId);
    if (result == FriendResult.SUCCESS) {
      hint("Removed " + PlayerIds.playerName(targetId), NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void acceptFriend(UUID requesterId) {
    FriendResult result = friendService.acceptRequest(player().getUniqueId(), requesterId);
    if (result == FriendResult.SUCCESS) {
      hint("Accepted friend request", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void declineFriend(UUID requesterId) {
    FriendResult result = friendService.declineRequest(player().getUniqueId(), requesterId);
    if (result == FriendResult.SUCCESS) {
      hint("Declined friend request", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void cancelFriend(UUID targetId) {
    FriendResult result = friendService.cancelRequest(player().getUniqueId(), targetId);
    if (result == FriendResult.SUCCESS) {
      hint("Cancelled friend request", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void inviteToParty(UUID actorId, UUID targetId) {
    PartyResult result = partyService.invite(actorId, targetId);
    if (result == PartyResult.SUCCESS) {
      hint("Invited " + PlayerIds.playerName(targetId), NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
  }

  private void acceptParty(UUID partyId) {
    PartyResult result = partyService.acceptInvite(player().getUniqueId(), partyId);
    if (result == PartyResult.SUCCESS) {
      hint("Joined party", NamedTextColor.GREEN);
      activeTab.set(SocialTab.PARTY);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void declineParty(UUID partyId) {
    PartyResult result = partyService.declineInvite(player().getUniqueId(), partyId);
    if (result == PartyResult.SUCCESS) {
      hint("Declined party invite", NamedTextColor.GREEN);
    } else {
      hint(describe(result), NamedTextColor.RED);
    }
    refresh();
    invalidate();
  }

  private void hint(String message, NamedTextColor color) {
    player().sendActionBar(Component.text(message, color));
  }

  private static String describe(FriendResult result) {
    return switch (result) {
      case SUCCESS -> "Success.";
      case SELF_REQUEST -> "You cannot add yourself.";
      case ALREADY_FRIENDS -> "You are already friends.";
      case REQUEST_EXISTS -> "A request is already pending.";
      case NO_REQUEST -> "No pending request.";
      case NOT_FRIENDS -> "You are not friends.";
    };
  }

  private static String describe(PartyResult result) {
    return switch (result) {
      case SUCCESS -> "Success.";
      case NOT_IN_PARTY -> "You are not in a party.";
      case ALREADY_IN_PARTY -> "Already in a party.";
      case TARGET_IN_PARTY -> "That player is already in a party.";
      case SELF_INVITE -> "You cannot invite yourself.";
      case ALREADY_INVITED -> "Already invited.";
      case PARTY_FULL -> "Party is full.";
      case NOT_LEADER -> "You are not the leader.";
      case NOT_A_MEMBER -> "Not a member.";
      case SELF_KICK -> "You cannot kick yourself.";
      case NO_INVITE -> "No pending invite.";
      case INVALID_NAME -> "Invalid party name.";
    };
  }
}
