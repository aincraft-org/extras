package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import dev.mintychochip.api.ChatResult;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultChatServiceTest {

  private InProcessExtrasEventService bus;
  private final List<ExtrasEvent> events = new ArrayList<>();

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    bus.subscribe(events::add);
  }

  private DefaultChatService newService() {
    return newService(new InMemoryChatRepository());
  }

  private DefaultChatService newService(InMemoryChatRepository repository) {
    return new DefaultChatService(repository, Clock.systemUTC(), bus);
  }

  @Test
  void absentUsersDefaultToGlobalWithNoMutes() {
    UUID playerId = UUID.randomUUID();
    DefaultChatService service = newService();

    assertEquals(
        new ChannelPreferences(playerId, ChannelId.GLOBAL, Set.of()),
        service.preferences(playerId));
  }

  @Test
  void selectingPersistsAndUnmutesSelectedChannel() {
    InMemoryChatRepository repository = new InMemoryChatRepository();
    UUID playerId = UUID.randomUUID();
    DefaultChatService service = newService(repository);

    assertEquals(ChatResult.SUCCESS, service.mute(playerId, ChannelId.LOCAL));
    assertEquals(ChatResult.SUCCESS, service.select(playerId, ChannelId.LOCAL));
    assertEquals(ChannelId.LOCAL, service.preferences(playerId).activeChannel());
    assertTrue(!service.preferences(playerId).mutedChannels().contains(ChannelId.LOCAL));
    assertEquals(ChannelId.LOCAL, repository.load(playerId).orElseThrow().activeChannel());
  }

  @Test
  void globalCannotBeMuted() {
    UUID playerId = UUID.randomUUID();
    DefaultChatService service = newService();

    assertEquals(ChatResult.GLOBAL_CANNOT_BE_MUTED, service.mute(playerId, ChannelId.GLOBAL));
  }

  @Test
  void globalMuteRequiresPlayerId() {
    DefaultChatService service = newService();

    assertThrows(NullPointerException.class, () -> service.mute(null, ChannelId.GLOBAL));
  }

  @Test
  void returnedMuteSetsAreImmutable() {
    UUID playerId = UUID.randomUUID();
    DefaultChatService service = newService();

    ChannelPreferences preferences = service.preferences(playerId);
    assertThrows(
        UnsupportedOperationException.class,
        () -> preferences.mutedChannels().add(ChannelId.LOCAL));
  }

  private static final class InMemoryChatRepository implements ChatRepository {
    private ChannelPreferences value;

    @Override
    public java.util.Optional<ChannelPreferences> load(UUID playerId) {
      return java.util.Optional.ofNullable(value);
    }

    @Override
    public void save(ChannelPreferences preferences) {
      value = preferences;
    }

    @Override
    public void close() {}
  }

  // --------------------------------------------------------------- events

  @Test
  void selectMuteUnmuteEmitEvents() {
    UUID playerId = UUID.randomUUID();
    DefaultChatService service = newService();

    assertEquals(ChatResult.SUCCESS, service.select(playerId, ChannelId.LOCAL));
    assertSingleEvent(
        ExtrasEvent.ChatChannelSelected.class,
        event -> {
          assertEquals(playerId, event.playerId());
          assertEquals("local", event.channelKey());
        });

    assertEquals(ChatResult.SUCCESS, service.mute(playerId, ChannelId.MARKET));
    assertSingleEvent(
        ExtrasEvent.ChatChannelMuted.class,
        event -> {
          assertEquals(playerId, event.playerId());
          assertEquals("market", event.channelKey());
        });

    assertEquals(ChatResult.SUCCESS, service.unmute(playerId, ChannelId.MARKET));
    assertSingleEvent(
        ExtrasEvent.ChatChannelUnmuted.class,
        event -> {
          assertEquals(playerId, event.playerId());
          assertEquals("market", event.channelKey());
        });
  }

  @Test
  void failedAndNoOpPreferencesEmitNoEvents() {
    UUID playerId = UUID.randomUUID();
    DefaultChatService service = newService();

    assertEquals(ChatResult.GLOBAL_CANNOT_BE_MUTED, service.mute(playerId, ChannelId.GLOBAL));
    assertEquals(ChatResult.NOT_MUTED, service.unmute(playerId, ChannelId.LOCAL));

    service.mute(playerId, ChannelId.LOCAL);
    events.clear();
    assertEquals(ChatResult.ALREADY_MUTED, service.mute(playerId, ChannelId.LOCAL));
    assertTrue(events.isEmpty());

    // Selecting an already-active channel still changes nothing observable but
    // unmutes it (no-op in practice); a repeat select is a committed success.
    service.select(playerId, ChannelId.LOCAL);
    events.clear();
    service.select(playerId, ChannelId.LOCAL);
    assertSingleEvent(
        ExtrasEvent.ChatChannelSelected.class, event -> assertEquals("local", event.channelKey()));
  }

  private <E extends ExtrasEvent> void assertSingleEvent(
      Class<E> type, java.util.function.Consumer<E> assertions) {
    assertEquals(1, events.size(), "expected exactly one event, got: " + events);
    ExtrasEvent event = events.get(0);
    assertTrue(type.isInstance(event), "expected " + type.getSimpleName() + " but got " + event);
    assertions.accept(type.cast(event));
    events.clear();
  }
}
