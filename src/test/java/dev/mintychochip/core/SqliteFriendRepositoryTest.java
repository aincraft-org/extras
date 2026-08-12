package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.FriendRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Persistence and restart behavior of the SQLite friend store. */
class SqliteFriendRepositoryTest {

  @TempDir Path tempDir;

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();
  private final UUID carol = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-08-08T12:00:00Z");

  private SqliteFriendRepository newRepository(Path file) {
    return new SqliteFriendRepository(file);
  }

  @Test
  void requestsAndFriendshipsSurviveCloseAndReopen() {
    Path file = tempDir.resolve("friends.db");
    SqliteFriendRepository first = newRepository(file);
    first.upsertRequest(alice, bob, now);
    first.addFriendship(carol, alice, now.plusSeconds(10));
    first.close();

    SqliteFriendRepository second = newRepository(file);
    assertEquals(Optional.of(now), second.findRequest(alice, bob));
    assertTrue(second.findRequest(bob, alice).isEmpty());
    List<FriendRequest> incoming = second.findIncoming(bob);
    assertEquals(1, incoming.size());
    assertEquals(alice, incoming.get(0).requesterId());
    assertEquals(now, incoming.get(0).createdAt());

    assertEquals(Optional.of(now.plusSeconds(10)), second.findFriendship(carol, alice));
    assertEquals(Optional.of(now.plusSeconds(10)), second.findFriendship(alice, carol));
    assertEquals(List.of(carol), second.findFriendIds(alice));
    assertEquals(List.of(alice), second.findFriendIds(carol));
    second.close();
  }

  @Test
  void friendshipPairIsStoredCanonically() {
    SqliteFriendRepository repository = newRepository(tempDir.resolve("friends.db"));
    repository.addFriendship(bob, alice, now); // reversed argument order

    assertEquals(Optional.of(now), repository.findFriendship(alice, bob));
    assertEquals(List.of(bob), repository.findFriendIds(alice));
    assertEquals(List.of(alice), repository.findFriendIds(bob));
    repository.close();
  }

  @Test
  void deleteOperationsRemoveOnlyTheirRows() {
    SqliteFriendRepository repository = newRepository(tempDir.resolve("friends.db"));
    repository.upsertRequest(alice, bob, now);
    repository.upsertRequest(carol, bob, now.plusSeconds(1));
    repository.addFriendship(alice, carol, now.plusSeconds(2));

    repository.deleteRequest(alice, bob);

    assertTrue(repository.findRequest(alice, bob).isEmpty());
    assertEquals(
        List.of(carol),
        repository.findIncoming(bob).stream().map(FriendRequest::requesterId).toList());
    assertEquals(Optional.of(now.plusSeconds(2)), repository.findFriendship(alice, carol));

    repository.deleteFriendship(alice, carol);
    assertTrue(repository.findFriendship(alice, carol).isEmpty());
    assertTrue(repository.findFriendIds(alice).isEmpty());
    repository.close();
  }

  @Test
  void addFriendshipIsIdempotent() {
    SqliteFriendRepository repository = newRepository(tempDir.resolve("friends.db"));
    repository.addFriendship(alice, bob, now);
    repository.addFriendship(alice, bob, now.plusSeconds(5));
    repository.addFriendship(bob, alice, now.plusSeconds(6));

    assertEquals(Optional.of(now), repository.findFriendship(alice, bob));
    assertEquals(1, repository.findFriendIds(alice).size());
    repository.close();
  }

  @Test
  void upsertRefreshesRequestTimestamp() {
    SqliteFriendRepository repository = newRepository(tempDir.resolve("friends.db"));
    repository.upsertRequest(alice, bob, now);
    repository.upsertRequest(alice, bob, now.plusSeconds(30));

    assertEquals(Optional.of(now.plusSeconds(30)), repository.findRequest(alice, bob));
    assertEquals(1, repository.findIncoming(bob).size());
    repository.close();
  }
}
