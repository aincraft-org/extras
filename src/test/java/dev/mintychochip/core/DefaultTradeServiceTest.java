package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.TradeResult;
import dev.mintychochip.api.TradeService;
import dev.mintychochip.api.TradeSnapshot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultTradeServiceTest {

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();
  private final UUID carol = UUID.randomUUID();

  @Test
  void acceptsRequestAndRequiresBothCurrentConfirmations() {
    TradeService service = new DefaultTradeService();

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
    TradeService service = acceptedTrade();
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
    TradeService service = new DefaultTradeService();

    assertEquals(TradeResult.SELF_TRADE, service.request(alice, alice));
    assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
    assertEquals(TradeResult.REQUEST_EXISTS, service.request(alice, bob));
    assertEquals(TradeResult.NOT_PARTICIPANT, service.cancel(carol));
    assertEquals(TradeResult.SUCCESS, service.decline(bob));
    assertTrue(service.pendingRequestFrom(bob).isEmpty());
  }

  @Test
  void onePlayerCannotParticipateInTwoTrades() {
    TradeService service = acceptedTrade();

    assertEquals(TradeResult.ALREADY_TRADING, service.request(alice, carol));
    assertEquals(TradeResult.ALREADY_TRADING, service.request(carol, bob));
  }

  private TradeService acceptedTrade() {
    TradeService service = new DefaultTradeService();
    assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
    assertEquals(TradeResult.SUCCESS, service.accept(bob));
    return service;
  }
}
