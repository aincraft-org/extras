package dev.mintychochip.core;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Owns the single SQLite {@link Connection} for party persistence.
 *
 * <p>All repository access is serialized on this one connection: SQLite is a single-writer store,
 * and the JDBC driver is not required to be multithreaded across {@code Statement}/{@code
 * ResultSet} instances on a shared connection. Schema DDL runs once at construction; foreign keys
 * are enabled so {@code party_members}/{@code party_invites} cascade with {@code parties} deletion.
 */
final class SqliteConnection implements AutoCloseable {

  private static final String[] SCHEMA = {
    SqlStatements.load("party/create-parties.sql"),
    SqlStatements.load("party/create-party-members.sql"),
    SqlStatements.load("party/create-party-invites.sql")
  };

  private final HikariDataSource dataSource;
  private final Connection connection;

  SqliteConnection(String jdbcUrl) {
    this(jdbcUrl, SCHEMA);
  }

  SqliteConnection(String jdbcUrl, String[] schema) {
    try {
      this.dataSource = SqlitePool.open(jdbcUrl);
      this.connection = dataSource.getConnection();
      try (var statement = connection.createStatement()) {
        statement.execute(SqlStatements.load("pragma-foreign-keys.sql"));
        for (String ddl : schema) {
          statement.execute(ddl);
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to open SQLite store at " + jdbcUrl, e);
    }
  }

  HikariDataSource dataSource() {
    return dataSource;
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
   * Returns {@code [a, b]} ordered by {@link UUID#compareTo} so an unordered pair of players maps
   * to exactly one canonical row.
   */
  static UUID[] canonicalPair(UUID a, UUID b) {
    return a.compareTo(b) <= 0 ? new UUID[] {a, b} : new UUID[] {b, a};
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
    dataSource.close();
  }
}
