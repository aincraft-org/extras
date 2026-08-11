package dev.mintychochip.core;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Owns the single SQLite {@link Connection} for party persistence.
 *
 * <p>All repository access is serialized on this one connection: SQLite is a
 * single-writer store, and the JDBC driver is not required to be
 * multithreaded across {@code Statement}/{@code ResultSet} instances on a
 * shared connection. Schema DDL runs once at construction; foreign keys are
 * enabled so {@code party_members}/{@code party_invites} cascade with
 * {@code parties} deletion.
 */
final class SqliteConnection implements AutoCloseable {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS parties (
              party_id   BLOB PRIMARY KEY,
              name       TEXT,
              leader     BLOB NOT NULL,
              created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS party_members (
              party_id  BLOB NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
              member    BLOB NOT NULL,
              joined_at INTEGER NOT NULL,
              PRIMARY KEY (party_id, member)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS party_invites (
              party_id   BLOB NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
              invitee    BLOB NOT NULL,
              inviter    BLOB NOT NULL,
              expires_at INTEGER NOT NULL,
              PRIMARY KEY (party_id, invitee)
            )
            """
    };

    private final Connection connection;

    SqliteConnection(String jdbcUrl) {
        this(jdbcUrl, SCHEMA);
    }

    SqliteConnection(String jdbcUrl, String[] schema) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(jdbcUrl);
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                for (String ddl : schema) {
                    statement.execute(ddl);
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to open SQLite store at " + jdbcUrl, e);
        }
    }

    Connection connection() {
        return connection;
    }

    static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * Returns {@code [a, b]} ordered by {@link UUID#compareTo} so an unordered
     * pair of players maps to exactly one canonical row.
     */
    static UUID[] canonicalPair(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? new UUID[] { a, b } : new UUID[] { b, a };
    }

    static UUID uuidFromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do on close failure.
        }
    }
}
