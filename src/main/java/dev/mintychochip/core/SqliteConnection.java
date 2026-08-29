package dev.mintychochip.core;

import com.zaxxer.hikari.HikariConfig;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.aincraft.db.sql.SqlDatabase;
import org.jdbi.v3.core.Handle;

final class SqliteConnection implements AutoCloseable {

  private static final String LEGACY_BASELINE_SQL =
      "CREATE TABLE IF NOT EXISTS flyway_schema_history ("
          + "installed_rank INTEGER NOT NULL,"
          + "version VARCHAR(50),"
          + "description VARCHAR(200) NOT NULL,"
          + "type VARCHAR(20) NOT NULL,"
          + "script VARCHAR(1000) NOT NULL,"
          + "checksum INTEGER,"
          + "installed_by VARCHAR(100) NOT NULL,"
          + "installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
          + "execution_time INTEGER NOT NULL,"
          + "success BOOLEAN NOT NULL,"
          + "CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank))";
  private static final String LEGACY_BASELINE_INDEX_SQL =
      "CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx "
          + "ON flyway_schema_history (success)";
  private static final String LEGACY_BASELINE_INSERT_SQL =
      "INSERT INTO flyway_schema_history "
          + "(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) "
          + "VALUES (1,'0','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'Extras',0,1)";

  private final SqlDatabase database;

  SqliteConnection(String jdbcUrl, String migrationLocation) {
    this.database = createWithPluginClassLoader(jdbcUrl, migrationLocation);
  }

  private static SqlDatabase createWithPluginClassLoader(String jdbcUrl, String migrationLocation) {
    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(SqliteConnection.class.getClassLoader());
    try {
      return create(jdbcUrl, migrationLocation);
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  private static SqlDatabase create(String jdbcUrl, String migrationLocation) {
    try {
      String path =
          jdbcUrl.startsWith("jdbc:sqlite:") ? jdbcUrl.substring("jdbc:sqlite:".length()) : null;
      if (path != null && !":memory:".equals(path)) {
        Path file = Path.of(path).toAbsolutePath();
        Path parent = file.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
      }
      Class.forName("org.sqlite.JDBC");
      HikariConfig config = new HikariConfig();
      config.setDriverClassName("org.sqlite.JDBC");
      config.setJdbcUrl(jdbcUrl);
      config.setMaximumPoolSize(1);
      config.setPoolName("extras-sqlite");
      config.setConnectionInitSql("PRAGMA foreign_keys = ON");
      SqlDatabase sqlDatabase = SqlDatabase.create(config, migrationLocation);
      try {
        boolean hasHistory =
            sqlDatabase
                .jdbi()
                .withHandle(
                    handle ->
                        handle
                                .createQuery(
                                    "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'flyway_schema_history')")
                                .mapTo(Integer.class)
                                .one()
                            != 0);
        boolean hasUserTables =
            sqlDatabase
                .jdbi()
                .withHandle(
                    handle ->
                        handle
                                .createQuery(
                                    "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'flyway_schema_history')")
                                .mapTo(Integer.class)
                                .one()
                            != 0);
        baselineLegacyDatabase(sqlDatabase, hasHistory, hasUserTables);
        sqlDatabase.migrate();
      } catch (RuntimeException failure) {
        sqlDatabase.close();
        throw failure;
      }
      return sqlDatabase;
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("SQLite JDBC driver is missing", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to create SQLite database directory", e);
    }
  }

  private static void baselineLegacyDatabase(
      SqlDatabase database, boolean hasHistory, boolean hasUserTables) {
    if (hasHistory || !hasUserTables) {
      return;
    }
    database
        .jdbi()
        .useTransaction(
            handle -> {
              handle.execute(LEGACY_BASELINE_SQL);
              handle.execute(LEGACY_BASELINE_INDEX_SQL);
              handle.execute(LEGACY_BASELINE_INSERT_SQL);
            });
  }

  <T> T withHandle(Function<Handle, T> callback) {
    return database.jdbi().withHandle(handle -> callback.apply(handle));
  }

  <T> T inTransaction(Function<Handle, T> callback) {
    return database.jdbi().inTransaction(handle -> callback.apply(handle));
  }

  void useTransaction(Consumer<Handle> callback) {
    database.jdbi().useTransaction(handle -> callback.accept(handle));
  }

  SqlDatabase database() {
    return database;
  }

  static byte[] uuidToBytes(UUID uuid) {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }

  /** Returns {@code [a, b]} in UUID order for canonical unordered-pair storage. */
  static UUID[] canonicalPair(UUID a, UUID b) {
    return a.compareTo(b) <= 0 ? new UUID[] {a, b} : new UUID[] {b, a};
  }

  static UUID uuidFromBytes(byte[] bytes) {
    if (bytes == null || bytes.length != 16) {
      throw new IllegalArgumentException("UUID value must contain 16 bytes");
    }
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  @Override
  public void close() {
    database.close();
  }
}
