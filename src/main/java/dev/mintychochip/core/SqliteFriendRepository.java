package dev.mintychochip.core;

import dev.mintychochip.api.FriendRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jdbi.v3.core.Handle;

/** SQLite-backed {@link FriendRepository} using the Utilities SQL lifecycle. */
public final class SqliteFriendRepository implements FriendRepository {

  private final SqliteConnection sqlite;

  public SqliteFriendRepository(Path databaseFile) {
    this(
        new SqliteConnection(
            "jdbc:sqlite:" + databaseFile.toAbsolutePath(), "classpath:db/migration/friend"));
  }

  SqliteFriendRepository(SqliteConnection sqlite) {
    this.sqlite = Objects.requireNonNull(sqlite, "sqlite");
  }

  @Override
  public Optional<Instant> findRequest(UUID requesterId, UUID targetId) {
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("friend/select-request.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(requesterId))
                  .bind(1, SqliteConnection.uuidToBytes(targetId))
                  .map((row, context) -> Instant.ofEpochMilli(row.getLong("created_at")))
                  .findOne());
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Failed to read request from " + requesterId + " to " + targetId, e);
    }
  }

  @Override
  public List<FriendRequest> findIncoming(UUID targetId) {
    return findRequests(SqlStatements.load("friend/select-incoming.sql"), targetId, true);
  }

  @Override
  public List<FriendRequest> findOutgoing(UUID requesterId) {
    return findRequests(SqlStatements.load("friend/select-outgoing.sql"), requesterId, false);
  }

  private List<FriendRequest> findRequests(String sql, UUID playerId, boolean incoming) {
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(sql)
                  .bind(0, SqliteConnection.uuidToBytes(playerId))
                  .map(
                      (row, context) -> {
                        UUID other = SqliteConnection.uuidFromBytes(row.getBytes(1));
                        Instant createdAt = Instant.ofEpochMilli(row.getLong("created_at"));
                        return incoming
                            ? new FriendRequest(other, playerId, createdAt)
                            : new FriendRequest(playerId, other, createdAt);
                      })
                  .list());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to read requests for " + playerId, e);
    }
  }

  @Override
  public Optional<Instant> findFriendship(UUID playerA, UUID playerB) {
    UUID[] pair = SqliteConnection.canonicalPair(playerA, playerB);
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("friend/select-friendship.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(pair[0]))
                  .bind(1, SqliteConnection.uuidToBytes(pair[1]))
                  .map((row, context) -> Instant.ofEpochMilli(row.getLong("since")))
                  .findOne());
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Failed to read friendship between " + playerA + " and " + playerB, e);
    }
  }

  @Override
  public List<UUID> findFriendIds(UUID playerId) {
    byte[] id = SqliteConnection.uuidToBytes(playerId);
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("friend/select-friend-ids.sql"))
                  .bind(0, id)
                  .bind(1, id)
                  .bind(2, id)
                  .map((row, context) -> SqliteConnection.uuidFromBytes(row.getBytes("friend")))
                  .list());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to read friends of " + playerId, e);
    }
  }

  @Override
  public void upsertRequest(UUID requesterId, UUID targetId, Instant createdAt) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("friend/upsert-request.sql"))
                .bind(0, SqliteConnection.uuidToBytes(requesterId))
                .bind(1, SqliteConnection.uuidToBytes(targetId))
                .bind(2, createdAt.toEpochMilli())
                .execute());
  }

  @Override
  public void deleteRequest(UUID requesterId, UUID targetId) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("friend/delete-request.sql"))
                .bind(0, SqliteConnection.uuidToBytes(requesterId))
                .bind(1, SqliteConnection.uuidToBytes(targetId))
                .execute());
  }

  @Override
  public void addFriendship(UUID playerA, UUID playerB, Instant since) {
    UUID[] pair = SqliteConnection.canonicalPair(playerA, playerB);
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("friend/insert-friendship.sql"))
                .bind(0, SqliteConnection.uuidToBytes(pair[0]))
                .bind(1, SqliteConnection.uuidToBytes(pair[1]))
                .bind(2, since.toEpochMilli())
                .execute());
  }

  @Override
  public void deleteFriendship(UUID playerA, UUID playerB) {
    UUID[] pair = SqliteConnection.canonicalPair(playerA, playerB);
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("friend/delete-friendship.sql"))
                .bind(0, SqliteConnection.uuidToBytes(pair[0]))
                .bind(1, SqliteConnection.uuidToBytes(pair[1]))
                .execute());
  }

  private void transaction(Consumer<Handle> action) {
    try {
      sqlite.useTransaction(action);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Friend store transaction failed", e);
    }
  }

  @Override
  public void close() {
    sqlite.close();
  }
}
