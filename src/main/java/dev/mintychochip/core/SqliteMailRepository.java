package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
 * SQLite-backed {@link MailRepository}: one {@code mail} table in a single
 * database file. Synchronous, single-writer (SQLite serializes writes).
 */
public final class SqliteMailRepository implements MailRepository {

    private static final String CREATE_SCHEMA = """
            CREATE TABLE IF NOT EXISTS mail (
              id          INTEGER PRIMARY KEY AUTOINCREMENT,
              recipient   TEXT    NOT NULL,
              sender_name TEXT    NOT NULL,
              body        TEXT    NOT NULL,
              sent_at     INTEGER NOT NULL,
              read        INTEGER NOT NULL DEFAULT 0,
              claimed     INTEGER NOT NULL DEFAULT 0,
              attachment  TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_mail_recipient_id ON mail(recipient, id);
            CREATE INDEX IF NOT EXISTS idx_mail_recipient_read ON mail(recipient, read);
            """;

    private final Connection connection;

    public SqliteMailRepository(Path dbFile) {
        Objects.requireNonNull(dbFile, "dbFile");
        try {
            Path parent = dbFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_SCHEMA);
            }
        } catch (SQLException e) {
            throw new UncheckedIOException("Failed to open mail database " + dbFile, new IOException(e));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create mail database parent dir " + dbFile, e);
        }
    }

    @Override
    public synchronized MailMessage insert(MailMessage mail) {
        String sql = """
                INSERT INTO mail (recipient, sender_name, body, sent_at, read, claimed, attachment)
                VALUES (?, ?, ?, ?, ?, 0, ?)
                """;
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
                return new MailMessage(keys.getLong(1), mail.recipient(), mail.senderName(),
                        mail.body(), mail.sentAtMillis(), mail.read(), mail.attachment());
            }
        } catch (SQLException e) {
            throw new UncheckedIOException("Failed to insert mail", new IOException(e));
        }
    }

    @Override
    public synchronized List<MailMessage> list(UUID recipient, int page, int pageSize) {
        String sql = """
                SELECT id, recipient, sender_name, body, sent_at, read, attachment
                FROM mail
                WHERE recipient = ?
                ORDER BY sent_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
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
        return scalarInt("SELECT COUNT(*) FROM mail WHERE recipient = ?", recipient);
    }

    @Override
    public synchronized int unreadCount(UUID recipient) {
        return scalarInt("SELECT COUNT(*) FROM mail WHERE recipient = ? AND read = 0", recipient);
    }

    @Override
    public synchronized void markRead(UUID recipient, long mailId) {
        update("UPDATE mail SET read = 1 WHERE recipient = ? AND id = ?", recipient, mailId);
    }

    @Override
    public synchronized void markUnread(UUID recipient, long mailId) {
        update("UPDATE mail SET read = 0 WHERE recipient = ? AND id = ?", recipient, mailId);
    }

    @Override
    public synchronized Optional<String> claim(UUID recipient, long mailId) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT attachment FROM mail WHERE recipient = ? AND id = ? AND claimed = 0")) {
                select.setString(1, recipient.toString());
                select.setLong(2, mailId);
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String blob = rs.getString(1);
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE mail SET claimed = 1 WHERE recipient = ? AND id = ? AND claimed = 0")) {
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
            throw new UncheckedIOException("Failed to claim attachment for mail " + mailId, new IOException(e));
        }
    }

    @Override
    public synchronized boolean delete(UUID recipient, long mailId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM mail WHERE recipient = ? AND id = ?")) {
            ps.setString(1, recipient.toString());
            ps.setLong(2, mailId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new UncheckedIOException("Failed to delete mail " + mailId, new java.io.IOException(e));
        }
    }

    @Override
    public synchronized int deleteAllRead(UUID recipient) {
        // Spec clear rule: only READ and (no attachment or already-claimed)
        // messages are bulk-deleted. A read letter whose item is not yet
        // claimed must survive so the player can still claim it.
        String sql = "DELETE FROM mail WHERE recipient = ? AND read = 1"
                + " AND (attachment IS NULL OR claimed = 1)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recipient.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedIOException("Failed to delete read mail for " + recipient, new java.io.IOException(e));
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new UncheckedIOException("Failed to close mail database", new IOException(e));
        }
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

    private void update(String sql, UUID recipient, long mailId) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recipient.toString());
            ps.setLong(2, mailId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedIOException("Failed to update mail " + mailId, new IOException(e));
        }
    }
}
