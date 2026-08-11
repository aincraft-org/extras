package dev.mintychochip.core;

import dev.mintychochip.api.FriendRequest;
import dev.mintychochip.api.FriendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Friend lifecycle: request, accept/decline, cancel, remove, and read views.
 */
class DefaultFriendServiceTest {

    @TempDir
    Path tempDir;

    private DefaultFriendService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DefaultFriendService(
                new SqliteFriendRepository(tempDir.resolve("friends-" + UUID.randomUUID() + ".db")),
                Clock.systemUTC());
    }

    // ------------------------------------------------------------- request

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
        // A reverse-direction request while the forward one is pending is also a duplicate pair.
        assertEquals(FriendResult.REQUEST_EXISTS, service.sendRequest(bob, alice));
    }

    @Test
    void sendRequestRejectedWhenAlreadyFriends() {
        service.sendRequest(alice, bob);
        service.acceptRequest(bob, alice);
        assertEquals(FriendResult.ALREADY_FRIENDS, service.sendRequest(bob, alice));
        assertEquals(FriendResult.ALREADY_FRIENDS, service.sendRequest(alice, bob));
    }

    // ------------------------------------------------- accept / decline

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
        assertEquals(FriendResult.NO_REQUEST, service.acceptRequest(alice, bob)); // wrong recipient
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
        // carol's request to bob survives.
        assertEquals(1, service.incomingRequests(bob).size());
        assertEquals(carol, service.incomingRequests(bob).get(0).requesterId());
        // alice can request bob again after a decline.
        assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
    }

    @Test
    void declineRequiresPendingRequest() {
        assertEquals(FriendResult.NO_REQUEST, service.declineRequest(bob, alice));
    }

    // -------------------------------------------------------------- cancel

    @Test
    void requesterCanCancelOnlyOwnRequest() {
        service.sendRequest(alice, bob);

        assertEquals(FriendResult.NO_REQUEST, service.cancelRequest(bob, alice)); // non-requester
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

    // ------------------------------------------------------------- remove

    @Test
    void removeBreaksMutualFriendship() {
        service.sendRequest(alice, bob);
        service.acceptRequest(bob, alice);

        assertEquals(FriendResult.NOT_FRIENDS, service.removeFriend(alice, carol));
        assertEquals(FriendResult.SUCCESS, service.removeFriend(alice, bob));

        assertFalse(service.areFriends(alice, bob));
        assertTrue(service.friendIdsOf(alice).isEmpty());
        assertTrue(service.friendIdsOf(bob).isEmpty());
        // After removal the pair can request again.
        assertEquals(FriendResult.SUCCESS, service.sendRequest(bob, alice));
    }

    // ---------------------------------------------------------- read views

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
}
