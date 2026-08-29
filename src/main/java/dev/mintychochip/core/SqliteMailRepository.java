package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.db.sql.SqlDatabase;

/** SQLite-backed {@link MailRepository} using the Utilities SQL lifecycle. */
public final class SqliteMailRepository implements MailRepository, AutoCloseable {

  private final SqliteConnection database;

  public SqliteMailRepository(Path dbFile) {
    Objects.requireNonNull(dbFile, "dbFile");
    this.database =
        new SqliteConnection(
            "jdbc:sqlite:" + dbFile.toAbsolutePath(), "classpath:db/migration/mail");
  }

  /** Exposes the utility-managed database for package-level integration checks. */
  SqlDatabase database() {
    return database.database();
  }

  @Override
  public synchronized MailMessage insert(MailMessage mail) {
    Objects.requireNonNull(mail, "mail");
    try {
      return database.withHandle(
          handle -> {
            long id =
                handle
                    .createUpdate(SqlStatements.load("mail/insert.sql"))
                    .bind(0, mail.recipient().toString())
                    .bind(1, mail.senderName())
                    .bind(2, mail.body())
                    .bind(3, mail.sentAtMillis())
                    .bind(4, mail.read() ? 1 : 0)
                    .bind(5, mail.attachment())
                    .executeAndReturnGeneratedKeys()
                    .mapTo(Long.class)
                    .one();
            return new MailMessage(
                id,
                mail.recipient(),
                mail.senderName(),
                mail.body(),
                mail.sentAtMillis(),
                mail.read(),
                mail.attachment());
          });
    } catch (RuntimeException e) {
      throw failure("Failed to insert mail", e);
    }
  }

  @Override
  public synchronized List<MailMessage> list(UUID recipient, int page, int pageSize) {
    try {
      return database.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("mail/list.sql"))
                  .bind(0, recipient.toString())
                  .bind(1, pageSize)
                  .bind(2, page * pageSize)
                  .map((row, context) -> mapRow(row))
                  .list());
    } catch (RuntimeException e) {
      throw failure("Failed to list mail for " + recipient, e);
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
      return database.inTransaction(
          handle -> {
            Optional<String> attachment =
                handle
                    .createQuery(SqlStatements.load("mail/select-unclaimed-attachment.sql"))
                    .bind(0, recipient.toString())
                    .bind(1, mailId)
                    .mapTo(String.class)
                    .findOne();
            if (attachment.isEmpty()) {
              return Optional.empty();
            }
            int updated =
                handle
                    .createUpdate(SqlStatements.load("mail/mark-claimed.sql"))
                    .bind(0, recipient.toString())
                    .bind(1, mailId)
                    .execute();
            return updated == 1 ? attachment : Optional.empty();
          });
    } catch (RuntimeException e) {
      throw failure("Failed to claim attachment for mail " + mailId, e);
    }
  }

  @Override
  public synchronized boolean delete(UUID recipient, long mailId) {
    try {
      return database.withHandle(
              handle ->
                  handle
                      .createUpdate(SqlStatements.load("mail/delete.sql"))
                      .bind(0, recipient.toString())
                      .bind(1, mailId)
                      .execute())
          > 0;
    } catch (RuntimeException e) {
      throw failure("Failed to delete mail " + mailId, e);
    }
  }

  @Override
  public synchronized List<Long> deletedIdsAllRead(UUID recipient) {
    try {
      return database.inTransaction(
          handle -> {
            List<Long> deletedIds =
                handle
                    .createQuery(SqlStatements.load("mail/select-ids-all-read.sql"))
                    .bind(0, recipient.toString())
                    .mapTo(Long.class)
                    .list();
            handle
                .createUpdate(SqlStatements.load("mail/delete-all-read.sql"))
                .bind(0, recipient.toString())
                .execute();
            return deletedIds;
          });
    } catch (RuntimeException e) {
      throw failure("Failed to delete read mail for " + recipient, e);
    }
  }

  @Override
  public synchronized void close() {
    database.close();
  }

  private static MailMessage mapRow(java.sql.ResultSet row) throws java.sql.SQLException {
    return new MailMessage(
        row.getLong("id"),
        UUID.fromString(row.getString("recipient")),
        row.getString("sender_name"),
        row.getString("body"),
        row.getLong("sent_at"),
        row.getInt("read") != 0,
        row.getString("attachment"));
  }

  private int scalarInt(String sql, UUID recipient) {
    try {
      return database.withHandle(
          handle ->
              handle.createQuery(sql).bind(0, recipient.toString()).mapTo(Integer.class).one());
    } catch (RuntimeException e) {
      throw failure("Failed to query mail count", e);
    }
  }

  private boolean updateChanged(String sql, UUID recipient, long mailId) {
    try {
      return database.withHandle(
              handle ->
                  handle.createUpdate(sql).bind(0, recipient.toString()).bind(1, mailId).execute())
          > 0;
    } catch (RuntimeException e) {
      throw failure("Failed to update mail " + mailId, e);
    }
  }

  private static UncheckedIOException failure(String message, RuntimeException cause) {
    return new UncheckedIOException(message, new IOException(cause));
  }
}
