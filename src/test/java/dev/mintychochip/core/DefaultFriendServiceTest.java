package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.FriendRequest;
import dev.mintychochip.api.FriendResult;
import dev.mintychochip.api.events.ExtrasEvent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Friend lifecycle: request, accept/decline, cancel, remove, and read views. */
class DefaultFriendServiceTest {

  @TempDir Path tempDir;

  private DefaultFriendService service;
  private InProcessExtrasEventService bus;
  private final List<ExtrasEvent> events = new ArrayList<>();

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();
  private final UUID carol = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failure -> {});
    bus.subscribe(events::add);
    service =
        new DefaultFriendService(
            new SqliteFriendRepository(tempDir.resolve("friends-" + UUID.randomUUID() + ".db")),
            Clock.systemUTC(),
            bus);
  }

  @Test
  void sendRequestCreatesDirectionalPendingRequest() {
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    List<FriendRequest> incoming = service.incomingRequests(bob);
    assertEquals(1, incoming.size());
    assertEquals(alice, incoming.get(0).requesterId());
    assertEquals(bob, incoming.get(0).targetId());
    List<FriendRequest> outgoing = service.outgoingRequests(alice);
    assertEquals(1, outgoing.size());
    assertEquals(alice, outgoing.get(0).requesterId());
    assertEquals(bob, outgoing.get(0).targetId());
  }

  @Test
  void sendRequestRejectsSelfAndExistingPairs() {
    assertEquals(FriendResult.SELF_REQUEST, service.sendRequest(alice, alice));
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    assertEquals(FriendResult.REQUEST_EXISTS, service.sendRequest(alice, bob));
    assertEquals(FriendResult.REQUEST_EXISTS, service.sendRequest(bob, alice));
  }

  @Test
  void sendRequestRejectedWhenAlreadyFriends() {
    service.sendRequest(alice, bob);
    service.acceptRequest(bob, alice);
    assertEquals(FriendResult.ALREADY_FRIENDS, service.sendRequest(bob, alice));
    assertEquals(FriendResult.ALREADY_FRIENDS, service.sendRequest(alice, bob));
  }

  @Test
  void acceptRequestCreatesMutualFriendship() {
    service.sendRequest(alice, bob);
    assertEquals(FriendResult.SUCCESS, service.acceptRequest(bob, alice));
    assertTrue(service.areFriends(alice, bob));
    assertTrue(service.areFriends(bob, alice));
    assertEquals(List.of(bob), service.friendIdsOf(alice));
    assertEquals(List.of(alice), service.friendIdsOf(bob));
    assertTrue(service.incomingRequests(bob).isEmpty());
    assertTrue(service.outgoingRequests(alice).isEmpty());
  }

  @Test
  void acceptRequiresPendingRequestForThatPair() {
    assertEquals(FriendResult.NO_REQUEST, service.acceptRequest(bob, alice));
    service.sendRequest(alice, bob);
    assertEquals(FriendResult.NO_REQUEST, service.acceptRequest(alice, bob));
    assertEquals(FriendResult.NO_REQUEST, service.acceptRequest(bob, carol));
  }

  @Test
  void acceptRejectsDuplicateRequestOnAlreadyFriends() {
    service.sendRequest(alice, bob);
    service.acceptRequest(bob, alice);
    assertEquals(FriendResult.ALREADY_FRIENDS, service.acceptRequest(bob, alice));
  }

  @Test
  void declineRemovesRequestOnly() {
    service.sendRequest(alice, bob);
    service.sendRequest(carol, bob);
    assertEquals(FriendResult.SUCCESS, service.declineRequest(bob, alice));
    assertFalse(service.areFriends(alice, bob));
    assertEquals(0, service.friendIdsOf(alice).size());
    assertEquals(1, service.incomingRequests(bob).size());
    assertEquals(carol, service.incomingRequests(bob).get(0).requesterId());
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
  }

  @Test
  void declineRequiresPendingRequest() {
    assertEquals(FriendResult.NO_REQUEST, service.declineRequest(bob, alice));
  }

  @Test
  void requesterCanCancelOnlyOwnRequest() {
    service.sendRequest(alice, bob);
    assertEquals(FriendResult.NO_REQUEST, service.cancelRequest(bob, alice));
    assertEquals(FriendResult.SUCCESS, service.cancelRequest(alice, bob));
    assertFalse(service.areFriends(alice, bob));
    assertTrue(service.outgoingRequests(alice).isEmpty());
    assertTrue(service.incomingRequests(bob).isEmpty());
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
  }

  @Test
  void cancelRequiresPendingRequest() {
    assertEquals(FriendResult.NO_REQUEST, service.cancelRequest(alice, bob));
  }

  @Test
  void removeBreaksMutualFriendship() {
    service.sendRequest(alice, bob);
    service.acceptRequest(bob, alice);
    assertEquals(FriendResult.NOT_FRIENDS, service.removeFriend(alice, carol));
    assertEquals(FriendResult.SUCCESS, service.removeFriend(alice, bob));
    assertFalse(service.areFriends(alice, bob));
    assertTrue(service.friendIdsOf(alice).isEmpty());
    assertTrue(service.friendIdsOf(bob).isEmpty());
    assertEquals(FriendResult.SUCCESS, service.sendRequest(bob, alice));
  }

  @Test
  void unknownPlayerReadViewsAreEmpty() {
    assertFalse(service.areFriends(alice, bob));
    assertTrue(service.incomingRequests(alice).isEmpty());
    assertTrue(service.outgoingRequests(alice).isEmpty());
    assertTrue(service.friendIdsOf(alice).isEmpty());
  }

  @Test
  void friendIdsAreDirectionAgnostic() {
    service.sendRequest(bob, alice);
    service.acceptRequest(alice, bob);
    assertEquals(List.of(bob), service.friendIdsOf(alice));
    assertEquals(List.of(alice), service.friendIdsOf(bob));
  }

  @Test
  void readViewsAreImmutable() {
    service.sendRequest(alice, bob);
    List<FriendRequest> incoming = service.incomingRequests(bob);
    assertThrows(UnsupportedOperationException.class, () -> incoming.clear());
    assertEquals(1, service.incomingRequests(bob).size());
  }

  @Test
  void createdAndAcceptedEmitEvents() {
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    assertSingleEvent(
        ExtrasEvent.FriendRequestCreated.class,
        event -> {
          assertEquals(alice, event.requesterId());
          assertEquals(bob, event.targetId());
        });
    assertEquals(FriendResult.SUCCESS, service.acceptRequest(bob, alice));
    assertSingleEvent(
        ExtrasEvent.FriendRequestAccepted.class,
        event -> {
          assertEquals(alice, event.requesterId());
          assertEquals(bob, event.recipientId());
        });
  }

  @Test
  void declineAndCancelEmitEvents() {
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    events.clear();
    assertEquals(FriendResult.SUCCESS, service.declineRequest(bob, alice));
    assertSingleEvent(
        ExtrasEvent.FriendRequestDeclined.class, event -> assertEquals(bob, event.recipientId()));
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    events.clear();
    assertEquals(FriendResult.SUCCESS, service.cancelRequest(alice, bob));
    assertSingleEvent(
        ExtrasEvent.FriendRequestCancelled.class,
        event -> assertEquals(alice, event.requesterId()));
  }

  @Test
  void friendshipRemovalEmitsEvent() {
    service.sendRequest(alice, bob);
    service.acceptRequest(bob, alice);
    events.clear();
    assertEquals(FriendResult.SUCCESS, service.removeFriend(alice, bob));
    assertSingleEvent(
        ExtrasEvent.FriendshipRemoved.class, event -> assertEquals(bob, event.targetId()));
  }

  @Test
  void failedMutationsEmitNoEvents() {
    assertEquals(FriendResult.SELF_REQUEST, service.sendRequest(alice, alice));
    assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    events.clear();
    assertEquals(FriendResult.REQUEST_EXISTS, service.sendRequest(alice, bob));
    assertEquals(FriendResult.REQUEST_EXISTS, service.sendRequest(bob, alice));
    assertEquals(FriendResult.NO_REQUEST, service.acceptRequest(bob, carol));
    assertEquals(FriendResult.NO_REQUEST, service.declineRequest(alice, bob));
    assertEquals(FriendResult.NO_REQUEST, service.cancelRequest(bob, alice));
    assertEquals(FriendResult.NOT_FRIENDS, service.removeFriend(alice, bob));
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
