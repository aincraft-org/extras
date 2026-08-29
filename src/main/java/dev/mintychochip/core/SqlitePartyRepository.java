package dev.mintychochip.core;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jdbi.v3.core.Handle;

/** SQLite-backed {@link PartyRepository} using the Utilities SQL lifecycle. */
public final class SqlitePartyRepository implements PartyRepository {

  private final SqliteConnection sqlite;

  public SqlitePartyRepository(Path databaseFile) {
    this(
        new SqliteConnection(
            "jdbc:sqlite:" + databaseFile.toAbsolutePath(), "classpath:db/migration/party"));
  }

  SqlitePartyRepository(SqliteConnection sqlite) {
    this.sqlite = Objects.requireNonNull(sqlite, "sqlite");
  }

  @Override
  public Optional<Party> findById(UUID partyId) {
    Objects.requireNonNull(partyId, "partyId");
    try {
      return sqlite.withHandle(handle -> findParty(handle, partyId));
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to read party " + partyId, e);
    }
  }

  private Optional<Party> findParty(Handle handle, UUID partyId) {
    byte[] id = SqliteConnection.uuidToBytes(partyId);
    return handle
        .createQuery(SqlStatements.load("party/select-by-id.sql"))
        .bind(0, id)
        .map(
            (row, context) ->
                new PartyRow(
                    row.getString("name"),
                    SqliteConnection.uuidFromBytes(row.getBytes("leader")),
                    Instant.ofEpochMilli(row.getLong("created_at"))))
        .findOne()
        .map(
            row ->
                new Party(
                    partyId,
                    row.name(),
                    row.leader(),
                    membersOf(handle, partyId),
                    row.createdAt()));
  }

  @Override
  public Optional<Party> findByMember(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("party/select-id-by-member.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(playerId))
                  .map((row, context) -> SqliteConnection.uuidFromBytes(row.getBytes("party_id")))
                  .findFirst()
                  .flatMap(partyId -> findParty(handle, partyId)));
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to find party of member " + playerId, e);
    }
  }

  @Override
  public List<PartyInvite> findPendingInvites(UUID playerId, Instant now) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(now, "now");
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("party/select-pending-invites.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(playerId))
                  .bind(1, now.toEpochMilli())
                  .map(
                      (row, context) ->
                          new PartyInvite(
                              SqliteConnection.uuidFromBytes(row.getBytes("party_id")),
                              SqliteConnection.uuidFromBytes(row.getBytes("inviter")),
                              playerId,
                              Instant.ofEpochMilli(row.getLong("expires_at"))))
                  .list());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to read invites for " + playerId, e);
    }
  }

  @Override
  public Optional<PartyInvite> findInvite(UUID partyId, UUID invitee, Instant now) {
    Objects.requireNonNull(partyId, "partyId");
    Objects.requireNonNull(invitee, "invitee");
    Objects.requireNonNull(now, "now");
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("party/select-invite.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(partyId))
                  .bind(1, SqliteConnection.uuidToBytes(invitee))
                  .bind(2, now.toEpochMilli())
                  .map(
                      (row, context) ->
                          new PartyInvite(
                              partyId,
                              SqliteConnection.uuidFromBytes(row.getBytes("inviter")),
                              invitee,
                              Instant.ofEpochMilli(row.getLong("expires_at"))))
                  .findOne());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to read invite for " + invitee + " to " + partyId, e);
    }
  }

  private List<UUID> membersOf(Handle handle, UUID partyId) {
    return handle
        .createQuery(SqlStatements.load("party/select-members.sql"))
        .bind(0, SqliteConnection.uuidToBytes(partyId))
        .map((row, context) -> SqliteConnection.uuidFromBytes(row.getBytes("member")))
        .list();
  }

  @Override
  public List<PartyInvite> findPendingInvitesUnbounded(UUID partyId) {
    Objects.requireNonNull(partyId, "partyId");
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("party/select-invites-by-party.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(partyId))
                  .map(
                      (row, context) ->
                          new PartyInvite(
                              partyId,
                              SqliteConnection.uuidFromBytes(row.getBytes("inviter")),
                              SqliteConnection.uuidFromBytes(row.getBytes("invitee")),
                              Instant.ofEpochMilli(row.getLong("expires_at"))))
                  .list());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to read invites of party " + partyId, e);
    }
  }

  @Override
  public void reassignInviteInviter(UUID partyId, UUID oldLeader, UUID newLeader) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("party/reassign-invite-inviter.sql"))
                .bind(0, SqliteConnection.uuidToBytes(newLeader))
                .bind(1, SqliteConnection.uuidToBytes(partyId))
                .bind(2, SqliteConnection.uuidToBytes(oldLeader))
                .execute());
  }

  @Override
  public void createParty(UUID partyId, String name, UUID leaderId, Instant createdAt) {
    transaction(
        handle -> {
          handle
              .createUpdate(SqlStatements.load("party/insert.sql"))
              .bind(0, SqliteConnection.uuidToBytes(partyId))
              .bind(1, name)
              .bind(2, SqliteConnection.uuidToBytes(leaderId))
              .bind(3, createdAt.toEpochMilli())
              .execute();
          insertMember(handle, partyId, leaderId, createdAt);
        });
  }

  @Override
  public void deleteParty(UUID partyId) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("party/delete.sql"))
                .bind(0, SqliteConnection.uuidToBytes(partyId))
                .execute());
  }

  @Override
  public void addMember(UUID partyId, UUID memberId, Instant joinedAt) {
    transaction(handle -> insertMember(handle, partyId, memberId, joinedAt));
  }

  private static void insertMember(Handle handle, UUID partyId, UUID memberId, Instant joinedAt) {
    handle
        .createUpdate(SqlStatements.load("party/insert-member.sql"))
        .bind(0, SqliteConnection.uuidToBytes(partyId))
        .bind(1, SqliteConnection.uuidToBytes(memberId))
        .bind(2, joinedAt.toEpochMilli())
        .execute();
  }

  @Override
  public void removeMember(UUID partyId, UUID memberId) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("party/delete-member.sql"))
                .bind(0, SqliteConnection.uuidToBytes(partyId))
                .bind(1, SqliteConnection.uuidToBytes(memberId))
                .execute());
  }

  @Override
  public void setLeader(UUID partyId, UUID leaderId) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("party/set-leader.sql"))
                .bind(0, SqliteConnection.uuidToBytes(leaderId))
                .bind(1, SqliteConnection.uuidToBytes(partyId))
                .execute());
  }

  @Override
  public void upsertInvite(UUID partyId, UUID invitee, UUID inviter, Instant expiresAt) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("party/upsert-invite.sql"))
                .bind(0, SqliteConnection.uuidToBytes(partyId))
                .bind(1, SqliteConnection.uuidToBytes(invitee))
                .bind(2, SqliteConnection.uuidToBytes(inviter))
                .bind(3, expiresAt.toEpochMilli())
                .execute());
  }

  @Override
  public void deleteInvite(UUID partyId, UUID invitee) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("party/delete-invite.sql"))
                .bind(0, SqliteConnection.uuidToBytes(partyId))
                .bind(1, SqliteConnection.uuidToBytes(invitee))
                .execute());
  }

  @Override
  public void acceptInvite(UUID partyId, UUID invitee, Instant joinedAt) {
    transaction(
        handle -> {
          insertMember(handle, partyId, invitee, joinedAt);
          handle
              .createUpdate(SqlStatements.load("party/delete-invite.sql"))
              .bind(0, SqliteConnection.uuidToBytes(partyId))
              .bind(1, SqliteConnection.uuidToBytes(invitee))
              .execute();
        });
  }

  @Override
  public void leaderLeaves(UUID partyId, UUID oldLeader, UUID newLeader) {
    transaction(
        handle -> {
          handle
              .createUpdate(SqlStatements.load("party/set-leader.sql"))
              .bind(0, SqliteConnection.uuidToBytes(newLeader))
              .bind(1, SqliteConnection.uuidToBytes(partyId))
              .execute();
          handle
              .createUpdate(SqlStatements.load("party/delete-member.sql"))
              .bind(0, SqliteConnection.uuidToBytes(partyId))
              .bind(1, SqliteConnection.uuidToBytes(oldLeader))
              .execute();
        });
  }

  private void transaction(Consumer<Handle> action) {
    try {
      sqlite.useTransaction(action);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Party store transaction failed", e);
    }
  }

  @Override
  public void close() {
    sqlite.close();
  }

  private record PartyRow(String name, UUID leader, Instant createdAt) {}
}
