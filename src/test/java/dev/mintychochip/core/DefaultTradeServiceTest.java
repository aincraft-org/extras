package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.TradeResult;
import dev.mintychochip.api.TradeSnapshot;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTradeServiceTest {

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();
  private final UUID carol = UUID.randomUUID();

  private InProcessExtrasEventService bus;
  private final List<ExtrasEvent> events = new ArrayList<>();

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    bus.subscribe(events::add);
  }

  private DefaultTradeService newService() {
    return new DefaultTradeService(Clock.systemUTC(), bus);
  }

  @Test
  void acceptsRequestAndRequiresBothCurrentConfirmations() {
    DefaultTradeService service = newService();

    assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
    assertEquals(TradeResult.SUCCESS, service.accept(bob));
    TradeSnapshot trade = service.tradeOf(alice).orElseThrow();

    assertEquals(TradeResult.SUCCESS, service.confirm(alice, trade.offerVersionOf(alice)));
    assertEquals(
        TradeResult.NOT_CONFIRMED,
        service.complete(alice, trade.offerVersionOf(alice), trade.offerVersionOf(bob)));
    assertEquals(TradeResult.SUCCESS, service.confirm(bob, trade.offerVersionOf(bob)));
    assertEquals(
        TradeResult.SUCCESS,
        service.complete(alice, trade.offerVersionOf(alice), trade.offerVersionOf(bob)));
    assertTrue(service.tradeOf(alice).isEmpty());
  }

  @Test
  void offerVersionChangeInvalidatesBothConfirmations() {
    DefaultTradeService service = acceptedTrade();
    TradeSnapshot before = service.tradeOf(alice).orElseThrow();

    assertEquals(TradeResult.SUCCESS, service.confirm(alice, before.offerVersionOf(alice)));
    assertEquals(TradeResult.SUCCESS, service.confirm(bob, before.offerVersionOf(bob)));
    service.offerChanged(alice);

    TradeSnapshot after = service.tradeOf(alice).orElseThrow();
    assertFalse(after.confirmed(alice));
    assertFalse(after.confirmed(bob));
    assertTrue(after.offerVersionOf(alice) > before.offerVersionOf(alice));
  }

  @Test
  void rejectsInvalidRequestsAndAllowsOnlyParticipantsToCancel() {
    DefaultTradeService service = newService();

    assertEquals(TradeResult.SELF_TRADE, service.request(alice, alice));
    assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
    assertEquals(TradeResult.REQUEST_EXISTS, service.request(alice, bob));
    assertEquals(TradeResult.NOT_PARTICIPANT, service.cancel(carol));
    assertEquals(TradeResult.SUCCESS, service.decline(bob));
    assertTrue(service.pendingRequestFrom(bob).isEmpty());
  }

  @Test
  void onePlayerCannotParticipateInTwoTrades() {
    DefaultTradeService service = acceptedTrade();

    assertEquals(TradeResult.ALREADY_TRADING, service.request(alice, carol));
    assertEquals(TradeResult.ALREADY_TRADING, service.request(carol, bob));
  }

  private DefaultTradeService acceptedTrade() {
    DefaultTradeService service = newService();
    assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
    assertEquals(TradeResult.SUCCESS, service.accept(bob));
    return service;
  }

  // --------------------------------------------------------------- events

  @Test
  void requestAndAcceptEmitEvents() {
    DefaultTradeService service = newService();

    assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
    assertSingleEvent(
        ExtrasEvent.TradeRequested.class,
        event -> {
          assertEquals(alice, event.requesterId());
          assertEquals(bob, event.targetId());
        });

    assertEquals(TradeResult.SUCCESS, service.accept(bob));
    assertSingleEvent(
        ExtrasEvent.TradeAccepted.class,
        event -> {
          assertEquals(alice, event.requesterId());
          assertEquals(bob, event.accepteeId());
        });
  }

  @Test
  void declineEmitsEventWithRequesterAndDecliner() {
    DefaultTradeService service = newService();
    service.request(alice, bob);
    events.clear();

    assertEquals(TradeResult.SUCCESS, service.decline(bob));
    assertSingleEvent(
        ExtrasEvent.TradeDeclined.class,
        event -> {
          assertEquals(alice, event.requesterId());
          assertEquals(bob, event.playerId());
        });
  }

  @Test
  void cancelAndCompleteEmitEvents() {
    DefaultTradeService service = acceptedTrade();
    events.clear();
    UUID tradeId = service.tradeOf(alice).orElseThrow().tradeId();

    assertEquals(TradeResult.SUCCESS, service.cancel(alice));
    assertSingleEvent(
        ExtrasEvent.TradeCancelled.class,
        event -> {
          assertEquals(tradeId, event.tradeId());
          assertEquals(alice, event.cancelledBy());
        });

    service.request(alice, bob);
    service.accept(bob);
    TradeSnapshot trade = service.tradeOf(alice).orElseThrow();
    events.clear();
    service.confirm(alice, trade.offerVersionOf(alice));
    service.confirm(bob, trade.offerVersionOf(bob));
    events.clear();

    assertEquals(
        TradeResult.SUCCESS,
        service.complete(alice, trade.offerVersionOf(alice), trade.offerVersionOf(bob)));
    assertSingleEvent(
        ExtrasEvent.TradeCompleted.class,
        event -> {
          assertEquals(trade.tradeId(), event.tradeId());
          assertEquals(alice, event.firstPlayerId());
          assertEquals(bob, event.secondPlayerId());
        });
  }

  @Test
  void failedMutationsEmitNoEvents() {
    DefaultTradeService service = newService();

    assertEquals(TradeResult.SELF_TRADE, service.request(alice, alice));
    assertEquals(TradeResult.NO_REQUEST, service.accept(bob));
    assertEquals(TradeResult.NO_REQUEST, service.decline(bob));
    assertEquals(TradeResult.NOT_PARTICIPANT, service.cancel(bob));
    assertTrue(events.isEmpty());

    service.request(alice, bob);
    events.clear();
    assertEquals(TradeResult.REQUEST_EXISTS, service.request(alice, bob));
    assertTrue(events.isEmpty());

    service.accept(bob);
    events.clear();
    // Both players are now in a trade.
    assertEquals(TradeResult.ALREADY_TRADING, service.request(bob, carol));
    assertEquals(TradeResult.ALREADY_TRADING, service.request(carol, alice));
    assertTrue(events.isEmpty());
    TradeSnapshot trade = service.tradeOf(alice).orElseThrow();
    events.clear();
    assertEquals(TradeResult.STALE_OFFER, service.confirm(alice, trade.offerVersionOf(alice) + 1));
    assertEquals(TradeResult.NOT_CONFIRMED, service.complete(alice, 0, 0));
    service.confirm(alice, trade.offerVersionOf(alice));
    assertEquals(
        TradeResult.ALREADY_CONFIRMED, service.confirm(alice, trade.offerVersionOf(alice)));
    assertTrue(events.isEmpty());
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
