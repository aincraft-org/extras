package dev.mintychochip.core;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;
import dev.mintychochip.api.PartyResult;
import dev.mintychochip.core.DefaultPartyService;
import dev.mintychochip.core.SqlitePartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Party lifecycle: create, invite, accept/decline, expiry, cap, leader
 * transfer, kick, disband, immutable read views.
 */
class DefaultPartyServiceTest {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final int SIZE_LIMIT = 4;

    @TempDir
    Path tempDir;

    private Instant now;
    private DefaultPartyService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID dave = UUID.randomUUID();
    private final UUID erin = UUID.randomUUID();

    /** A clock whose instant can be advanced in tests. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceTo(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-08-08T12:00:00Z");
        clock = new MutableClock(now);
        service = new DefaultPartyService(
                new SqlitePartyRepository(tempDir.resolve("party-" + UUID.randomUUID() + ".db")),
                clock, TTL, SIZE_LIMIT);
    }

    // ----------------------------------------------------------- creation

    @Test
    void createPartyMakesLeaderFirstMember() {
        assertEquals(PartyResult.SUCCESS, service.createParty(alice, "raiders"));
        Optional<Party> party = service.partyOf(alice);
        assertTrue(party.isPresent());
        assertEquals("raiders", party.get().name());
        assertTrue(party.get().isLeader(alice));
        assertEquals(List.of(alice), party.get().memberIds());
    }

    @Test
    void createRejectedWhenAlreadyInParty() {
        assertEquals(PartyResult.SUCCESS, service.createParty(alice, null));
        assertEquals(PartyResult.ALREADY_IN_PARTY, service.createParty(alice, "second"));
    }

    @Test
    void createRejectsInvalidNames() {
        assertEquals(PartyResult.INVALID_NAME, service.createParty(alice, "   "));
        assertEquals(PartyResult.INVALID_NAME, service.createParty(alice, "a".repeat(33)));
        assertEquals(PartyResult.INVALID_NAME, service.createParty(alice, "tab\tname"));
        assertEquals(PartyResult.SUCCESS, service.createParty(bob, "  trimmed  "));
        assertEquals("trimmed", service.partyOf(bob).get().name());
    }

    // -------------------------------------------------------------- invites

    @Test
    void inviteAcceptDeclineLifecycle() {
        service.createParty(alice, "raiders");
        assertEquals(PartyResult.SUCCESS, service.invite(alice, bob));

        List<PartyInvite> invites = service.pendingInvitations(bob);
        assertEquals(1, invites.size());
        assertEquals(alice, invites.get(0).inviterId());

        assertEquals(PartyResult.SUCCESS, service.acceptInvite(bob, invites.get(0).partyId()));
        assertTrue(service.partyOf(bob).isPresent());
        assertTrue(service.partyOf(bob).get().hasMember(bob));
        assertTrue(service.pendingInvitations(bob).isEmpty());
    }

    @Test
    void inviteRequiresMembershipAndFreshTarget() {
        assertEquals(PartyResult.NOT_IN_PARTY, service.invite(alice, bob));
        service.createParty(alice, null);
        assertEquals(PartyResult.SELF_INVITE, service.invite(alice, alice));
        assertEquals(PartyResult.SUCCESS, service.invite(alice, bob));
        assertEquals(PartyResult.ALREADY_INVITED, service.invite(alice, bob));
    }

    @Test
    void inviteExpiresAndCannotBeAccepted() {
        service.createParty(alice, null);
        assertEquals(PartyResult.SUCCESS, service.invite(alice, bob));

        clock.advanceTo(now.plusSeconds(61));
        assertTrue(service.pendingInvitations(bob).isEmpty());
        assertEquals(PartyResult.NO_INVITE,
                service.acceptInvite(bob, service.partyOf(alice).get().partyId()));
    }

    @Test
    void acceptRejectedWhenTargetAlreadyInParty() {
        service.createParty(alice, null);
        service.createParty(bob, null);
        service.invite(alice, carol);
        UUID aliceParty = service.partyOf(alice).get().partyId();
        assertEquals(PartyResult.ALREADY_IN_PARTY, service.acceptInvite(bob, aliceParty));
    }

    @Test
    void acceptRejectedWhenPartyFull() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.acceptInvite(bob, partyId);
        clock.advanceTo(now.plusSeconds(1));
        service.invite(alice, carol);
        service.acceptInvite(carol, partyId);
        clock.advanceTo(now.plusSeconds(2));
        service.invite(alice, dave);
        service.acceptInvite(dave, partyId);

        // Party is full at 4 members: an invite is rejected and a stale accept
        // (no invite was ever created for erin) finds no invite.
        assertEquals(PartyResult.PARTY_FULL, service.invite(alice, erin));
        assertEquals(PartyResult.NO_INVITE, service.acceptInvite(erin, partyId));
    }

    @Test
    void declineConsumesInvite() {
        service.createParty(alice, null);
        service.invite(alice, bob);
        UUID partyId = service.partyOf(alice).get().partyId();
        assertEquals(PartyResult.SUCCESS, service.declineInvite(bob, partyId));
        assertTrue(service.pendingInvitations(bob).isEmpty());
        assertEquals(PartyResult.NO_INVITE, service.acceptInvite(bob, partyId));
    }

    @Test
    void concurrentAcceptsRespectCap() throws Exception {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.invite(alice, carol);
        service.invite(alice, dave);
        service.invite(alice, erin);
        // 4 invites for a cap of 4; the party has 1 member, so only 3 can join.

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch ready = new CountDownLatch(4);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<PartyResult>> futures = List.of(
                    executor.submit(() -> acceptWhenReady(bob, partyId, ready, go)),
                    executor.submit(() -> acceptWhenReady(carol, partyId, ready, go)),
                    executor.submit(() -> acceptWhenReady(dave, partyId, ready, go)),
                    executor.submit(() -> acceptWhenReady(erin, partyId, ready, go)));
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            long successes = 0;
            for (Future<PartyResult> future : futures) {
                if (future.get(5, TimeUnit.SECONDS) == PartyResult.SUCCESS) {
                    successes++;
                }
            }
            assertEquals(3, successes);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(4, service.partyOf(alice).get().memberIds().size());
    }

    private PartyResult acceptWhenReady(UUID player, UUID partyId, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PartyResult.NO_INVITE;
        }
        return service.acceptInvite(player, partyId);
    }

    // ------------------------------------------------------- leader transfer

    @Test
    void leaderLeavesTransfersToLongestStandingMember() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.acceptInvite(bob, partyId);
        clock.advanceTo(now.plusSeconds(1));
        service.invite(alice, carol);
        service.acceptInvite(carol, partyId);

        assertEquals(PartyResult.SUCCESS, service.leaveParty(alice));

        Optional<Party> party = service.partyOf(bob);
        assertTrue(party.isPresent());
        assertEquals(bob, party.get().leaderId());
        assertEquals(List.of(bob, carol), party.get().memberIds());
        assertFalse(party.get().memberIds().contains(alice));
    }

    @Test
    void lastMemberLeavingDeletesParty() {
        service.createParty(alice, null);
        assertEquals(PartyResult.SUCCESS, service.leaveParty(alice));
        assertTrue(service.partyOf(alice).isEmpty());
    }

    // ---------------------------------------------------------------- kick

    @Test
    void onlyLeaderCanKick() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.acceptInvite(bob, partyId);

        assertEquals(PartyResult.NOT_LEADER, service.kick(bob, alice));
        assertEquals(PartyResult.SELF_KICK, service.kick(alice, alice));
        assertEquals(PartyResult.NOT_A_MEMBER, service.kick(alice, carol));
        assertEquals(PartyResult.SUCCESS, service.kick(alice, bob));
        assertFalse(service.partyOf(bob).isPresent());
    }

    @Test
    void kickRemovesInvitesToThatParty() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.acceptInvite(bob, partyId);
        service.invite(alice, carol);

        assertEquals(PartyResult.SUCCESS, service.kick(alice, bob));
        // carol's invite survives the kick.
        assertEquals(1, service.pendingInvitations(carol).size());
    }

    // -------------------------------------------------------------- disband

    @Test
    void onlyLeaderCanDisbandAndPartyIsDeleted() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.acceptInvite(bob, partyId);

        assertEquals(PartyResult.NOT_LEADER, service.disband(bob));
        assertEquals(PartyResult.SUCCESS, service.disband(alice));
        assertTrue(service.partyOf(alice).isEmpty());
        assertTrue(service.partyOf(bob).isEmpty());
    }

    // ------------------------------------------------------------- transfer

    @Test
    void transferLeadershipMovesLeaderRole() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        clock.advanceTo(now.plusSeconds(1));
        service.acceptInvite(bob, partyId);

        assertEquals(PartyResult.NOT_A_MEMBER, service.transferLeadership(alice, carol));
        assertEquals(PartyResult.NOT_LEADER, service.transferLeadership(bob, alice));
        assertEquals(PartyResult.SUCCESS, service.transferLeadership(alice, bob));
        Optional<Party> party = service.partyOf(bob);
        assertTrue(party.isPresent());
        assertEquals(bob, party.get().leaderId());
        assertEquals(alice, party.get().memberIds().get(0));
    }

    // ------------------------------------------------------------ read views

    @Test
    void unknownPlayerReadViewsAreEmpty() {
        assertTrue(service.partyOf(alice).isEmpty());
        assertTrue(service.pendingInvitations(alice).isEmpty());
    }

    @Test
    void snapshotIsImmutable() {
        service.createParty(alice, null);
        Party snapshot = service.partyOf(alice).get();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.memberIds().add(bob));
        assertEquals(1, snapshot.memberIds().size());
        assertEquals(1, service.partyOf(alice).get().memberIds().size());
    }

    // ------------------------------------------------------------ logout

    @Test
    void leaderLogoutTransfersLeadershipAndKeepsMembership() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        clock.advanceTo(now.plusSeconds(1));
        service.acceptInvite(bob, partyId);
        service.invite(alice, carol);

        service.logout(alice);

        Optional<Party> party = service.partyOf(bob);
        assertTrue(party.isPresent());
        assertEquals(bob, party.get().leaderId());
        // Alice stays a member; the carol invite now points at bob.
        assertTrue(party.get().hasMember(alice));
        List<PartyInvite> carolInvites = service.pendingInvitations(carol);
        assertEquals(1, carolInvites.size());
        assertEquals(bob, carolInvites.get(0).inviterId());
        // Logging back in, alice is still a member under bob's leadership.
        Optional<Party> aliceView = service.partyOf(alice);
        assertTrue(aliceView.isPresent());
        assertEquals(bob, aliceView.get().leaderId());
    }

    @Test
    void memberLogoutOnlyEvictsCache() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        clock.advanceTo(now.plusSeconds(1));
        service.acceptInvite(bob, partyId);

        service.logout(bob);

        Optional<Party> party = service.partyOf(bob);
        assertTrue(party.isPresent());
        assertEquals(alice, party.get().leaderId());
        assertTrue(party.get().hasMember(bob));
    }

    @Test
    void soloLeaderLogoutKeepsParty() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();

        service.logout(alice);

        Optional<Party> party = service.partyOf(alice);
        assertTrue(party.isPresent());
        assertEquals(alice, party.get().leaderId());
        assertEquals(1, party.get().size());
    }

    // ------------------------------------------------------------ evict

    @Test
    void evictReloadsPartyFromStore() {
        service.createParty(alice, null);
        UUID partyId = service.partyOf(alice).get().partyId();
        service.invite(alice, bob);
        service.acceptInvite(bob, partyId);

        service.evict(alice);
        Optional<Party> reloaded = service.partyOf(bob);
        assertTrue(reloaded.isPresent());
        assertEquals(alice, reloaded.get().leaderId());
        assertEquals(2, reloaded.get().memberIds().size());
    }
}
