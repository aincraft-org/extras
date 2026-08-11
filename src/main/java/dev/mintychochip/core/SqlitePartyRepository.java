package dev.mintychochip.core;

import dev.mintychochip.api.Party;
import dev.mintychochip.api.PartyInvite;

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
 * SQLite-backed {@link PartyRepository}.
 *
 * <p>Every mutating operation runs inside one transaction on the shared
 * connection; reads commit nothing. Expired invites are filtered on read.
 */
public final class SqlitePartyRepository implements PartyRepository {

    private final SqliteConnection sqlite;

    public SqlitePartyRepository(Path databaseFile) {
        this(new SqliteConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath()));
    }

    SqlitePartyRepository(SqliteConnection sqlite) {
        this.sqlite = Objects.requireNonNull(sqlite, "sqlite");
    }

    @Override
    public Optional<Party> findById(UUID partyId) {
        byte[] id = SqliteConnection.uuidToBytes(partyId);
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT name, leader, created_at FROM parties WHERE party_id = ?")) {
            stmt.setBytes(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                List<UUID> members = membersOf(partyId);
                return Optional.of(new Party(
                        partyId,
                        rs.getString("name"),
                        SqliteConnection.uuidFromBytes(rs.getBytes("leader")),
                        members,
                        Instant.ofEpochMilli(rs.getLong("created_at"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read party " + partyId, e);
        }
    }

    @Override
    public Optional<Party> findByMember(UUID playerId) {
        byte[] member = SqliteConnection.uuidToBytes(playerId);
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT party_id FROM party_members WHERE member = ?")) {
            stmt.setBytes(1, member);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return findById(SqliteConnection.uuidFromBytes(rs.getBytes("party_id")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find party of member " + playerId, e);
        }
    }

    @Override
    public List<PartyInvite> findPendingInvites(UUID playerId, Instant now) {
        byte[] invitee = SqliteConnection.uuidToBytes(playerId);
        List<PartyInvite> result = new ArrayList<>();
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT party_id, inviter, expires_at FROM party_invites "
                        + "WHERE invitee = ? AND expires_at > ? "
                        + "ORDER BY expires_at DESC")) {
            stmt.setBytes(1, invitee);
            stmt.setLong(2, now.toEpochMilli());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID partyId = SqliteConnection.uuidFromBytes(rs.getBytes("party_id"));
                    result.add(new PartyInvite(
                            partyId,
                            SqliteConnection.uuidFromBytes(rs.getBytes("inviter")),
                            playerId,
                            Instant.ofEpochMilli(rs.getLong("expires_at"))));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read invites for " + playerId, e);
        }
    }

    @Override
    public Optional<PartyInvite> findInvite(UUID partyId, UUID invitee, Instant now) {
        byte[] party = SqliteConnection.uuidToBytes(partyId);
        byte[] inviteeBytes = SqliteConnection.uuidToBytes(invitee);
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT inviter, expires_at FROM party_invites "
                        + "WHERE party_id = ? AND invitee = ? AND expires_at > ?")) {
            stmt.setBytes(1, party);
            stmt.setBytes(2, inviteeBytes);
            stmt.setLong(3, now.toEpochMilli());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PartyInvite(
                        partyId,
                        SqliteConnection.uuidFromBytes(rs.getBytes("inviter")),
                        invitee,
                        Instant.ofEpochMilli(rs.getLong("expires_at"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read invite for " + invitee + " to " + partyId, e);
        }
    }

    private List<UUID> membersOf(UUID partyId) throws SQLException {
        byte[] party = SqliteConnection.uuidToBytes(partyId);
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT member FROM party_members WHERE party_id = ? ORDER BY joined_at ASC")) {
            stmt.setBytes(1, party);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(SqliteConnection.uuidFromBytes(rs.getBytes("member")));
                }
            }
        }
        return members;
    }

    @Override
    public List<PartyInvite> findPendingInvitesUnbounded(UUID partyId) {
        byte[] party = SqliteConnection.uuidToBytes(partyId);
        List<PartyInvite> result = new ArrayList<>();
        try (PreparedStatement stmt = sqlite.connection().prepareStatement(
                "SELECT invitee, inviter, expires_at FROM party_invites WHERE party_id = ?")) {
            stmt.setBytes(1, party);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new PartyInvite(
                            partyId,
                            SqliteConnection.uuidFromBytes(rs.getBytes("inviter")),
                            SqliteConnection.uuidFromBytes(rs.getBytes("invitee")),
                            Instant.ofEpochMilli(rs.getLong("expires_at"))));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read invites of party " + partyId, e);
        }
    }

    @Override
    public void reassignInviteInviter(UUID partyId, UUID oldLeader, UUID newLeader) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE party_invites SET inviter = ? "
                            + "WHERE party_id = ? AND inviter = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(newLeader));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(partyId));
                stmt.setBytes(3, SqliteConnection.uuidToBytes(oldLeader));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void createParty(UUID partyId, String name, UUID leaderId, Instant createdAt) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO parties (party_id, name, leader, created_at) VALUES (?, ?, ?, ?)")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.setString(2, name);
                stmt.setBytes(3, SqliteConnection.uuidToBytes(leaderId));
                stmt.setLong(4, createdAt.toEpochMilli());
                stmt.executeUpdate();
            }
            insertMember(connection, partyId, leaderId, createdAt);
            return null;
        });
    }

    @Override
    public void deleteParty(UUID partyId) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM parties WHERE party_id = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void addMember(UUID partyId, UUID memberId, Instant joinedAt) {
        inTransaction(connection -> {
            insertMember(connection, partyId, memberId, joinedAt);
            return null;
        });
    }

    private static void insertMember(Connection connection, UUID partyId, UUID memberId, Instant joinedAt)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO party_members (party_id, member, joined_at) VALUES (?, ?, ?)")) {
            stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
            stmt.setBytes(2, SqliteConnection.uuidToBytes(memberId));
            stmt.setLong(3, joinedAt.toEpochMilli());
            stmt.executeUpdate();
        }
    }

    @Override
    public void removeMember(UUID partyId, UUID memberId) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM party_members WHERE party_id = ? AND member = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(memberId));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void setLeader(UUID partyId, UUID leaderId) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE parties SET leader = ? WHERE party_id = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(leaderId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(partyId));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void upsertInvite(UUID partyId, UUID invitee, UUID inviter, Instant expiresAt) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO party_invites (party_id, invitee, inviter, expires_at) VALUES (?, ?, ?, ?) "
                            + "ON CONFLICT(party_id, invitee) DO UPDATE SET inviter = excluded.inviter, "
                            + "expires_at = excluded.expires_at")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(invitee));
                stmt.setBytes(3, SqliteConnection.uuidToBytes(inviter));
                stmt.setLong(4, expiresAt.toEpochMilli());
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteInvite(UUID partyId, UUID invitee) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM party_invites WHERE party_id = ? AND invitee = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(invitee));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void acceptInvite(UUID partyId, UUID invitee, Instant joinedAt) {
        inTransaction(connection -> {
            insertMember(connection, partyId, invitee, joinedAt);
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM party_invites WHERE party_id = ? AND invitee = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(invitee));
                stmt.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void leaderLeaves(UUID partyId, UUID oldLeader, UUID newLeader) {
        inTransaction(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE parties SET leader = ? WHERE party_id = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(newLeader));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(partyId));
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM party_members WHERE party_id = ? AND member = ?")) {
                stmt.setBytes(1, SqliteConnection.uuidToBytes(partyId));
                stmt.setBytes(2, SqliteConnection.uuidToBytes(oldLeader));
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
            throw new IllegalStateException("Party store transaction failed", e);
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
