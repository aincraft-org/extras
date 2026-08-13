package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import dev.mintychochip.api.ChatDelivery;
import dev.mintychochip.api.ChatMessage;
import dev.mintychochip.api.Party;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRouterTest {
  private static final UUID WORLD = UUID.randomUUID();
  private static final UUID OTHER_WORLD = UUID.randomUUID();
  private static final UUID SENDER = UUID.randomUUID();
  private static final UUID RECIPIENT = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  @Test
  void globalRoutesToEveryPresentPlayerIncludingSender() {
    ChatMessage message = message(ChannelId.GLOBAL, " hello ");

    ChatDelivery delivery =
        route(
            message,
            presence(SENDER, 0, 0, WORLD),
            List.of(presence(SENDER, 0, 0, WORLD), presence(RECIPIENT, 10, 10, WORLD)));

    assertEquals(Set.of(SENDER, RECIPIENT), delivery.recipients());
    assertEquals("hello", message.text());
  }

  @Test
  void localUsesSameWorldSquaredDistanceAndInclusiveHundredBlockBoundary() {
    PresenceSnapshot sender = presence(SENDER, 0, 0, WORLD);
    List<PresenceSnapshot> players =
        List.of(
            sender,
            presence(RECIPIENT, 99.9, 0, WORLD),
            presence(OTHER, 100.1, 0, WORLD),
            presence(UUID.randomUUID(), 60, 80, WORLD),
            presence(UUID.randomUUID(), 0, 0, OTHER_WORLD));

    ChatDelivery delivery = route(message(ChannelId.LOCAL, "local"), sender, players);

    assertEquals(Set.of(SENDER, RECIPIENT, players.get(3).playerId()), delivery.recipients());
  }

  @Test
  void partyRequiresPartyAndRoutesOnlyMembers() {
    PresenceSnapshot sender = presence(SENDER, 0, 0, WORLD);
    PresenceSnapshot member = presence(RECIPIENT, 1000, 1000, OTHER_WORLD);
    PresenceSnapshot outsider = presence(OTHER, 1000, 1000, OTHER_WORLD);
    List<PresenceSnapshot> players = List.of(sender, member, outsider);
    Party party = party(List.of(SENDER, RECIPIENT));

    assertEquals(
        Set.of(SENDER, RECIPIENT),
        route(message(ChannelId.PARTY, "party"), sender, players, Optional.of(party)).recipients());
    ChatDelivery absentParty =
        route(message(ChannelId.PARTY, "party"), sender, players, Optional.empty());
    assertEquals(false, absentParty.accepted());
    assertEquals(Set.of(), absentParty.recipients());
    ChatDelivery nonMemberSender =
        route(
            new ChatMessage(OTHER, ChannelId.PARTY, "party", Instant.EPOCH),
            presence(OTHER, 0, 0, WORLD),
            players,
            Optional.of(party));
    assertEquals(false, nonMemberSender.accepted());
    assertEquals(Set.of(), nonMemberSender.recipients());
  }

  @Test
  void marketAndLfgRouteGlobally() {
    PresenceSnapshot sender = presence(SENDER, 0, 0, WORLD);
    List<PresenceSnapshot> players = List.of(sender, presence(RECIPIENT, 0, 0, WORLD));

    assertEquals(
        Set.of(SENDER, RECIPIENT),
        route(message(ChannelId.MARKET, "market"), sender, players).recipients());
    assertEquals(
        Set.of(SENDER, RECIPIENT),
        route(message(ChannelId.LFG, "lfg"), sender, players).recipients());
  }

  @Test
  void mutedRecipientsAreFilteredButSenderIsAlwaysIncluded() {
    PresenceSnapshot sender = presence(SENDER, 0, 0, WORLD);
    List<PresenceSnapshot> players = List.of(sender, presence(RECIPIENT, 0, 0, WORLD));
    Map<UUID, ChannelPreferences> preferences =
        Map.of(
            SENDER, new ChannelPreferences(SENDER, ChannelId.GLOBAL, Set.of()),
            RECIPIENT,
                new ChannelPreferences(RECIPIENT, ChannelId.GLOBAL, Set.of(ChannelId.LOCAL)));

    ChatDelivery delivery =
        new ChatRouter()
            .route(
                message(ChannelId.LOCAL, "local"),
                sender,
                players,
                Optional.empty(),
                id -> preferences.get(id));

    assertEquals(Set.of(SENDER), delivery.recipients());
  }

  @Test
  void missingPresenceDoesNotReceiveLocationDependentLocalDelivery() {
    PresenceSnapshot sender = presence(SENDER, 0, 0, WORLD);
    assertEquals(
        Set.of(SENDER),
        route(message(ChannelId.LOCAL, "local"), sender, List.of(sender)).recipients());
  }

  @Test
  void messageRejectsEmptyAndMoreThan256UnicodeCodePoints() {
    assertThrows(IllegalArgumentException.class, () -> message(ChannelId.GLOBAL, "   "));
    assertThrows(IllegalArgumentException.class, () -> message(ChannelId.GLOBAL, "a".repeat(257)));
    assertEquals(
        256,
        message(ChannelId.GLOBAL, "😀".repeat(256))
            .text()
            .codePointCount(0, message(ChannelId.GLOBAL, "😀".repeat(256)).text().length()));
  }

  private static ChatMessage message(ChannelId channel, String text) {
    return new ChatMessage(SENDER, channel, text, Instant.EPOCH);
  }

  private static ChatDelivery route(
      ChatMessage message, PresenceSnapshot sender, List<PresenceSnapshot> players) {
    return route(message, sender, players, Optional.empty());
  }

  private static ChatDelivery route(
      ChatMessage message,
      PresenceSnapshot sender,
      List<PresenceSnapshot> players,
      Optional<Party> party) {
    return new ChatRouter().route(message, sender, players, party, id -> null);
  }

  private static PresenceSnapshot presence(UUID id, double x, double z, UUID world) {
    return new PresenceSnapshot(id, world, x, 64, z);
  }

  private static Party party(List<UUID> members) {
    return new Party(UUID.randomUUID(), "party", SENDER, members, Instant.EPOCH);
  }
}
