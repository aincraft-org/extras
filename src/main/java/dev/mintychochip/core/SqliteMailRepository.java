package dev.mintychochip.core;

import com.zaxxer.hikari.HikariDataSource;
import dev.mintychochip.api.MailMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed {@link MailRepository}: one {@code mail} table in a single database file.
 * Synchronous, single-writer (SQLite serializes writes).
 */
public final class SqliteMailRepository implements MailRepository, AutoCloseable {

  private final HikariDataSource dataSource;
  private final Connection connection;

  public SqliteMailRepository(Path dbFile) {
    Objects.requireNonNull(dbFile, "dbFile");
    try {
      Path parent = dbFile.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      this.dataSource = SqlitePool.open("jdbc:sqlite:" + dbFile.toAbsolutePath());
      this.connection = dataSource.getConnection();
      try (Statement statement = connection.createStatement()) {
        for (String ddl : SqlStatements.load("mail/create-schema.sql").split(";")) {
          String trimmed = ddl.trim();
          if (!trimmed.isEmpty()) {
            statement.execute(trimmed);
          }
        }
      }
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to open mail database " + dbFile, new IOException(e));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create mail database parent dir " + dbFile, e);
    }
  }

  HikariDataSource dataSource() {
    return dataSource;
  }

  @Override
  public synchronized MailMessage insert(MailMessage mail) {
    String sql = SqlStatements.load("mail/insert.sql");
    try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, mail.recipient().toString());
      ps.setString(2, mail.senderName());
      ps.setString(3, mail.body());
      ps.setLong(4, mail.sentAtMillis());
      ps.setInt(5, mail.read() ? 1 : 0);
      ps.setString(6, mail.attachment());
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No generated key for inserted mail");
        }
        return new MailMessage(
            keys.getLong(1),
            mail.recipient(),
            mail.senderName(),
            mail.body(),
            mail.sentAtMillis(),
            mail.read(),
            mail.attachment());
      }
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to insert mail", new IOException(e));
    }
  }

  @Override
  public synchronized List<MailMessage> list(UUID recipient, int page, int pageSize) {
    String sql = SqlStatements.load("mail/list.sql");
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, recipient.toString());
      ps.setInt(2, pageSize);
      ps.setInt(3, page * pageSize);
      List<MailMessage> result = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          result.add(mapRow(rs));
        }
      }
      return result;
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to list mail for " + recipient, new IOException(e));
    }
  }

  @Override
  public synchronized int count(UUID recipient) {
    return scalarInt(SqlStatements.load("mail/count.sql"), recipient);
  }

  @Override
  public synchronized int unreadCount(UUID recipient) {
    return scalarInt(SqlStatements.load("mail/unread-count.sql"), recipient);
  }

  @Override
  public synchronized boolean markRead(UUID recipient, long mailId) {
    return updateChanged(SqlStatements.load("mail/mark-read.sql"), recipient, mailId);
  }

  @Override
  public synchronized boolean markUnread(UUID recipient, long mailId) {
    return updateChanged(SqlStatements.load("mail/mark-unread.sql"), recipient, mailId);
  }

  @Override
  public synchronized Optional<String> claim(UUID recipient, long mailId) {
    try {
      connection.setAutoCommit(false);
      try (PreparedStatement select =
          connection.prepareStatement(SqlStatements.load("mail/select-unclaimed-attachment.sql"))) {
        select.setString(1, recipient.toString());
        select.setLong(2, mailId);
        try (ResultSet rs = select.executeQuery()) {
          if (!rs.next()) {
            return Optional.empty();
          }
          String blob = rs.getString(1);
          try (PreparedStatement update =
              connection.prepareStatement(SqlStatements.load("mail/mark-claimed.sql"))) {
            update.setString(1, recipient.toString());
            update.setLong(2, mailId);
            int updated = update.executeUpdate();
            if (updated != 1) {
              connection.rollback();
              return Optional.empty();
            }
          }
          return Optional.ofNullable(blob);
        }
      } finally {
        connection.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new UncheckedIOException(
          "Failed to claim attachment for mail " + mailId, new IOException(e));
    }
  }

  @Override
  public synchronized boolean delete(UUID recipient, long mailId) {
    try (PreparedStatement ps =
        connection.prepareStatement(SqlStatements.load("mail/delete.sql"))) {
      ps.setString(1, recipient.toString());
      ps.setLong(2, mailId);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to delete mail " + mailId, new java.io.IOException(e));
    }
  }

  @Override
  public synchronized List<Long> deletedIdsAllRead(UUID recipient) {
    // Spec clear rule: only READ and (no attachment or already-claimed)
    // messages are bulk-deleted. A read letter whose item is not yet
    // claimed must survive so the player can still claim it. The deleted
    // ids are captured inside the same transaction that deletes them.
    try {
      connection.setAutoCommit(false);
      List<Long> deletedIds = new ArrayList<>();
      try {
        try (PreparedStatement select =
            connection.prepareStatement(SqlStatements.load("mail/select-ids-all-read.sql"))) {
          select.setString(1, recipient.toString());
          try (ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
              deletedIds.add(rs.getLong(1));
            }
          }
        }
        try (PreparedStatement ps =
            connection.prepareStatement(SqlStatements.load("mail/delete-all-read.sql"))) {
          ps.setString(1, recipient.toString());
          ps.executeUpdate();
        }
        connection.commit();
        return deletedIds;
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new UncheckedIOException(
          "Failed to delete read mail for " + recipient, new java.io.IOException(e));
    } finally {
      try {
        connection.setAutoCommit(true);
      } catch (SQLException e) {
        throw new UncheckedIOException(
            "Failed to reset mail autocommit", new java.io.IOException(e));
      }
    }
  }

  @Override
  public synchronized void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to close mail database", new IOException(e));
    }
    dataSource.close();
  }

  private static MailMessage mapRow(ResultSet rs) throws SQLException {
    return new MailMessage(
        rs.getLong("id"),
        UUID.fromString(rs.getString("recipient")),
        rs.getString("sender_name"),
        rs.getString("body"),
        rs.getLong("sent_at"),
        rs.getInt("read") != 0,
        rs.getString("attachment"));
  }

  private int scalarInt(String sql, UUID recipient) {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, recipient.toString());
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to query mail count", new IOException(e));
    }
  }

  private boolean updateChanged(String sql, UUID recipient, long mailId) {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, recipient.toString());
      ps.setLong(2, mailId);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new UncheckedIOException("Failed to update mail " + mailId, new IOException(e));
    }
  }
}
