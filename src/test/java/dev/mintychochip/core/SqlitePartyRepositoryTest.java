package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Persistence and restart behavior of the SQLite party store. */
class SqlitePartyRepositoryTest {

  @TempDir Path tempDir;

  private final UUID partyId = UUID.randomUUID();
  private final UUID leader = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID invitee = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-08-08T12:00:00Z");

  private SqlitePartyRepository newRepository(Path file) {
    return new SqlitePartyRepository(file);
  }

  @Test
  void partySurvivesCloseAndReopen() {
    Path file = tempDir.resolve("party.db");
    SqlitePartyRepository first = newRepository(file);
    first.createParty(partyId, "raiders", leader, now);
    first.addMember(partyId, member, now.plusSeconds(10));
    first.upsertInvite(partyId, invitee, leader, now.plusSeconds(60));
    first.close();

    SqlitePartyRepository second = newRepository(file);
    Optional<Party> reloaded = second.findById(partyId);
    assertTrue(reloaded.isPresent());
    assertEquals(partyId, reloaded.get().partyId());
    assertEquals("raiders", reloaded.get().name());
    assertEquals(leader, reloaded.get().leaderId());
    assertEquals(List.of(leader, member), reloaded.get().memberIds());
    assertEquals(now, reloaded.get().createdAt());

    assertEquals(Optional.of(partyId), second.findByMember(member).map(Party::partyId));
    List<PartyInvite> invites = second.findPendingInvites(invitee, now.plusSeconds(30));
    assertEquals(1, invites.size());
    assertEquals(partyId, invites.get(0).partyId());
    assertEquals(leader, invites.get(0).inviterId());
    second.close();
  }

  @Test
  void deletePartyCascadesMembersAndInvites() {
    Path file = tempDir.resolve("party.db");
    SqlitePartyRepository repository = newRepository(file);
    repository.createParty(partyId, null, leader, now);
    repository.addMember(partyId, member, now.plusSeconds(10));
    repository.upsertInvite(partyId, invitee, leader, now.plusSeconds(60));

    repository.deleteParty(partyId);

    assertTrue(repository.findById(partyId).isEmpty());
    assertTrue(repository.findByMember(leader).isEmpty());
    assertTrue(repository.findByMember(member).isEmpty());
    assertTrue(repository.findPendingInvites(invitee, now.plusSeconds(30)).isEmpty());
    repository.close();
  }

  @Test
  void expiredInvitesAreFilteredFromReads() {
    Path file = tempDir.resolve("party.db");
    SqlitePartyRepository repository = newRepository(file);
    repository.createParty(partyId, null, leader, now);
    repository.upsertInvite(partyId, invitee, leader, now.plusSeconds(30));

    Instant past = now.plusSeconds(60);
    assertTrue(repository.findPendingInvites(invitee, past).isEmpty());
    assertTrue(repository.findInvite(partyId, invitee, past).isEmpty());
    repository.close();
  }

  @Test
  void leaderLeavesTransfersLeadershipAndRemovesOldLeader() {
    Path file = tempDir.resolve("party.db");
    SqlitePartyRepository repository = newRepository(file);
    repository.createParty(partyId, null, leader, now);
    repository.addMember(partyId, member, now.plusSeconds(10));

    repository.leaderLeaves(partyId, leader, member);

    Optional<Party> reloaded = repository.findById(partyId);
    assertTrue(reloaded.isPresent());
    assertEquals(member, reloaded.get().leaderId());
    assertEquals(List.of(member), reloaded.get().memberIds());
    assertFalse(reloaded.get().memberIds().contains(leader));
    repository.close();
  }

  @Test
  void acceptInviteAddsMemberAndConsumesInviteAtomically() {
    Path file = tempDir.resolve("party.db");
    SqlitePartyRepository repository = newRepository(file);
    repository.createParty(partyId, null, leader, now);
    repository.upsertInvite(partyId, invitee, leader, now.plusSeconds(60));

    repository.acceptInvite(partyId, invitee, now.plusSeconds(20));

    Optional<Party> reloaded = repository.findById(partyId);
    assertTrue(reloaded.isPresent());
    assertTrue(reloaded.get().memberIds().contains(invitee));
    assertFalse(repository.findInvite(partyId, invitee, now.plusSeconds(70)).isPresent());
    repository.close();
  }
}
