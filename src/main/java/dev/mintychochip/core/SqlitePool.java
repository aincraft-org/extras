package dev.mintychochip.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Builds the SQLite HikariCP pool used by Extras persistence. */
final class SqlitePool {

  private SqlitePool() {}

  static HikariDataSource open(String jdbcUrl) {
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("SQLite JDBC driver is missing", e);
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setMaximumPoolSize(1);
    config.setPoolName("extras-sqlite");
    return new HikariDataSource(config);
  }
}
