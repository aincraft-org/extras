package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.ChannelId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ChatCommandTest {
  @Test
  void parsesChannelSelectionAndSendArguments() {
    assertEquals(ChatCommand.Action.SELECT, ChatCommand.parseAction(new String[] {"local"}));
    assertEquals(
        ChatCommand.Action.SEND, ChatCommand.parseAction(new String[] {"send", "party", "hello"}));
    assertEquals(
        ChannelId.LOCAL, ChatCommand.parseChannel(new String[] {"local"}, 0).orElseThrow());
    assertEquals(
        ChannelId.PARTY,
        ChatCommand.parseChannel(new String[] {"send", "party", "hello"}, 1).orElseThrow());
    assertEquals(
        "hello world", ChatCommand.message(new String[] {"send", "party", "hello", "world"}, 2));
  }

  @Test
  void offersActionsChannelsAndCaseInsensitiveFiltering() {
    assertEquals(List.of("lfg", "local"), ChatCommand.suggestions("l"));
    assertTrue(
        ChatCommand.suggestions("")
            .containsAll(
                List.of(
                    "global",
                    "local",
                    "party",
                    "market",
                    "lfg",
                    "send",
                    "mute",
                    "unmute",
                    "status",
                    "channels",
                    "help")));
  }

  @Test
  void oneShotSendDoesNotChangePersistentChannel() {
    RecordingChatService service = new RecordingChatService();
    List<String> sent = new java.util.ArrayList<>();
    Player player =
        (Player)
            java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> null);

    ChatCommand.sendOnce(
        service,
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        player,
        ChannelId.MARKET,
        "selling diamonds",
        (recipient, channel, message) -> {
          assertTrue(recipient == player);
          sent.add(channel.key() + ":" + message);
        });

    assertEquals(0, service.selectCalls);
    assertEquals(List.of("market:selling diamonds"), sent);
  }

  @Test
  void oneShotSelectionSurvivesDispatchUntilMatchingEventConsumesIt() {
    Instant now = Instant.parse("2026-08-12T00:00:00Z");
    ChatListener.OneShotSelection selection =
        new ChatListener.OneShotSelection(ChannelId.LFG, "group up", now.plusSeconds(5));

    assertTrue(selection.matches("group up", now.plusSeconds(1)));
    assertEquals(false, selection.matches("unrelated", now.plusSeconds(1)));
    assertEquals(false, selection.matches("group up", now.plusSeconds(6)));
  }

  @Test
  void oneShotSelectionCanOnlyBeConsumedOnce() {
    Instant now = Instant.parse("2026-08-12T00:00:00Z");
    java.util.concurrent.ConcurrentMap<UUID, ChatListener.OneShotSelection> selections =
        new java.util.concurrent.ConcurrentHashMap<>();
    UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000009");
    selections.put(
        playerId,
        new ChatListener.OneShotSelection(ChannelId.MARKET, "selling", now.plusSeconds(5)));

    assertEquals(
        ChannelId.MARKET,
        ChatListener.consumeSelection(selections, playerId, "selling", now).orElseThrow());
    assertTrue(ChatListener.consumeSelection(selections, playerId, "selling", now).isEmpty());
  }

  @Test
  void channelPermissionUsesStableChannelKey() {
    assertEquals("extras.chat.channel.lfg", ChatCommand.permission(ChannelId.LFG));
  }

  private static final class RecordingChatService implements dev.mintychochip.api.ChatService {
    private int selectCalls;

    @Override
    public dev.mintychochip.api.ChannelPreferences preferences(UUID playerId) {
      return new dev.mintychochip.api.ChannelPreferences(
          playerId, ChannelId.GLOBAL, java.util.Set.of());
    }

    @Override
    public dev.mintychochip.api.ChatResult select(UUID playerId, ChannelId channel) {
      selectCalls++;
      return dev.mintychochip.api.ChatResult.SUCCESS;
    }

    @Override
    public dev.mintychochip.api.ChatResult mute(UUID playerId, ChannelId channel) {
      return dev.mintychochip.api.ChatResult.SUCCESS;
    }

    @Override
    public dev.mintychochip.api.ChatResult unmute(UUID playerId, ChannelId channel) {
      return dev.mintychochip.api.ChatResult.SUCCESS;
    }
  }
}
