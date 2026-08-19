package dev.mintychochip.core;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/** SQLite-backed persistent chat preferences. */
public final class SqliteChatRepository implements ChatRepository {

  private final SqliteConnection database;

  public SqliteChatRepository(Path file) {
    this.database =
        new SqliteConnection(
            "jdbc:sqlite:" + file.toAbsolutePath(),
            new String[] {
              SqlStatements.load("chat/create-preferences.sql"),
              SqlStatements.load("chat/create-muted-channels.sql")
            });
  }

  @Override
  public synchronized Optional<ChannelPreferences> load(UUID playerId) {
    try (var statement =
        database.connection().prepareStatement(SqlStatements.load("chat/select-active.sql"))) {
      statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        ChannelId active =
            ChannelId.parse(result.getString("active_channel")).orElse(ChannelId.GLOBAL);
        EnumSet<ChannelId> muted = EnumSet.noneOf(ChannelId.class);
        try (var mutedStatement =
            database.connection().prepareStatement(SqlStatements.load("chat/select-muted.sql"))) {
          mutedStatement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
          try (ResultSet mutedRows = mutedStatement.executeQuery()) {
            while (mutedRows.next()) {
              ChannelId.parse(mutedRows.getString("channel")).ifPresent(muted::add);
            }
          }
        }
        return Optional.of(new ChannelPreferences(playerId, active, muted));
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to load chat preferences", exception);
    }
  }

  @Override
  public synchronized void save(ChannelPreferences preferences) {
    var connection = database.connection();
    try {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(SqlStatements.load("chat/upsert-preferences.sql"))) {
        statement.setBytes(1, SqliteConnection.uuidToBytes(preferences.playerId()));
        statement.setString(2, preferences.activeChannel().key());
        statement.setLong(3, System.currentTimeMillis());
        statement.executeUpdate();
      }
      try (var delete = connection.prepareStatement(SqlStatements.load("chat/delete-muted.sql"))) {
        delete.setBytes(1, SqliteConnection.uuidToBytes(preferences.playerId()));
        delete.executeUpdate();
      }
      try (var insert = connection.prepareStatement(SqlStatements.load("chat/insert-muted.sql"))) {
        for (ChannelId channel : preferences.mutedChannels()) {
          insert.setBytes(1, SqliteConnection.uuidToBytes(preferences.playerId()));
          insert.setString(2, channel.key());
          insert.addBatch();
        }
        insert.executeBatch();
      }
      connection.commit();
    } catch (SQLException exception) {
      try {
        connection.rollback();
      } catch (SQLException rollbackException) {
        exception.addSuppressed(rollbackException);
      }
      throw new IllegalStateException("Failed to save chat preferences", exception);
    } finally {
      try {
        connection.setAutoCommit(true);
      } catch (SQLException exception) {
        throw new IllegalStateException("Failed to restore SQLite transaction mode", exception);
      }
    }
  }

  @Override
  public void close() {
    database.close();
  }
}
