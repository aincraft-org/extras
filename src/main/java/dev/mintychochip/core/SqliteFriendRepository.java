package dev.mintychochip.core;

import dev.mintychochip.api.FriendRequest;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed {@link FriendRepository}.
 *
 * <p>Every mutating operation runs inside one transaction on the shared
 * connection; reads commit nothing. Requests are stored directionally; each
 * unordered friendship occupies one canonical row ({@code playerA} &lt;
 * {@code playerB}).
 */
public final class SqliteFriendRepository implements FriendRepository {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS friend_requests (
              requester  BLOB NOT NULL,
              target     BLOB NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY (requester, target)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS friendships (
              player_a   BLOB NOT NULL,
              player_b   BLOB NOT NULL,
              since      INTEGER NOT NULL,
              PRIMARY KEY (player_a, player_b)
            )
            """
    };

    private final SqliteConnection sqlite;

    public SqliteFriendRepository(Path databaseFile) {
        this(new SqliteConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath(), SCHEMA));
    }

    SqliteFriendRepository(SqliteConnection sqlite) {
        this.sqlite = Objects.requireNonNull(sqlite, "sqlite");
    }

    @Override
    public Optional<Instant> findRequest(UUID requesterId, UUID targetId) {
        byte[] requester = SqliteConnection.uuidToBytes(requesterId);
        byte[] target = SqliteConnection.uuidToBytes(targetId);
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT created_at FROM friend_requests WHERE requester = ? AND target = ?")) {
            stmt.setBytes(1, requester);
            stmt.setBytes(2, target);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next()
                        ? Optional.of(Instant.ofEpochMilli(rs.getLong("created_at")))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read request from " + requesterId + " to " + targetId, e);
        }
    }

    @Override
    public List<FriendRequest> findIncoming(UUID targetId) {
        return findRequests("SELECT requester, created_at FROM friend_requests WHERE target = ? "
                + "ORDER BY created_at ASC", targetId, true);
    }

    @Override
    public List<FriendRequest> findOutgoing(UUID requesterId) {
        return findRequests("SELECT target, created_at FROM friend_requests WHERE requester = ? "
                + "ORDER BY created_at ASC", requesterId, false);
    }

    private List<FriendRequest> findRequests(String sql, UUID playerId, boolean incoming) {
        List<FriendRequest> result = new ArrayList<>();
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(sql)) {
            stmt.setBytes(1, SqliteConnection.uuidToBytes(playerId));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID other = SqliteConnection.uuidFromBytes(rs.getBytes(1));
                    Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
                    result.add(incoming
                            ? new FriendRequest(other, playerId, createdAt)
                            : new FriendRequest(playerId, other, createdAt));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read requests for " + playerId, e);
        }
    }

    @Override
    public Optional<Instant> findFriendship(UUID playerA, UUID playerB) {
        UUID[] pair = SqliteConnection.canonicalPair(playerA, playerB);
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT since FROM friendships WHERE player_a = ? AND player_b = ?")) {
            stmt.setBytes(1, SqliteConnection.uuidToBytes(pair[0]));
            stmt.setBytes(2, SqliteConnection.uuidToBytes(pair[1]));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next()
                        ? Optional.of(Instant.ofEpochMilli(rs.getLong("since")))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read friendship between " + playerA + " and " + playerB, e);
        }
    }

    @Override
    public List<UUID> findFriendIds(UUID playerId) {
        byte[] id = SqliteConnection.uuidToBytes(playerId);
        List<UUID> result = new ArrayList<>();
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT CASE WHEN player_a = ? THEN player_b ELSE player_a END AS friend "
                        + "FROM friendships WHERE player_a = ? OR player_b = ? "
                        + "ORDER BY since ASC")) {
            stmt.setBytes(1, id);
            stmt.setBytes(2, id);
            stmt.setBytes(3, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(SqliteConnection.uuidFromBytes(rs.getBytes("friend")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read friends of " + playerId, e);
        }
    }

    @Override
    public void upsertRequest(UUID requesterId, UUID targetId, Instant createdAt) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO friend_requests (requester, target, created_at) VALUES (?, ?, ?) "
                            + "ON CONFLICT(requester, target) DO UPDATE SET created_at = excluded.created_at")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(requesterId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(targetId));
                stmt.setLong(3, createdAt.toEpochMilli());
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteRequest(UUID requesterId, UUID targetId) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM friend_requests WHERE requester = ? AND target = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(requesterId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(targetId));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void addFriendship(UUID playerA, UUID playerB, Instant since) {
        UUID[] pair = SqliteConnection.canonicalPair(playerA, playerB);
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO friendships (player_a, player_b, since) VALUES (?, ?, ?)")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(pair[0]));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(pair[1]));
                stmt.setLong(3, since.toEpochMilli());
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteFriendship(UUID playerA, UUID playerB) {
        UUID[] pair = SqliteConnection.canonicalPair(playerA, playerB);
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM friendships WHERE player_a = ? AND player_b = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(pair[0]));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(pair[1]));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    private void inTransaction(TransactionAction action) {
        Connection connection = sqlite.connection();
        try {
            connection.setAutoCommit(false);
            action.run(connection);
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw new IllegalStateException("Friend store transaction failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // The connection is closing or already broken; nothing to restore.
            }
        }
    }

    @FunctionalInterface
    private interface TransactionAction {
        Void run(Connection connection) throws SQLException;
    }

    @Override
    public void close() {
        sqlite.close();
    }
}
