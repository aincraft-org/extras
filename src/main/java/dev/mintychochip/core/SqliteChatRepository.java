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
              "CREATE TABLE IF NOT EXISTS chat_preferences ("
                  + "player_id BLOB PRIMARY KEY, active_channel TEXT NOT NULL, updated_at INTEGER NOT NULL)",
              "CREATE TABLE IF NOT EXISTS chat_muted_channels ("
                  + "player_id BLOB NOT NULL, channel TEXT NOT NULL, "
                  + "PRIMARY KEY(player_id, channel), "
                  + "FOREIGN KEY(player_id) REFERENCES chat_preferences(player_id) ON DELETE CASCADE)"
            });
  }

  @Override
  public synchronized Optional<ChannelPreferences> load(UUID playerId) {
    try (var statement =
        database
            .connection()
            .prepareStatement("SELECT active_channel FROM chat_preferences WHERE player_id = ?")) {
      statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        ChannelId active =
            ChannelId.parse(result.getString("active_channel")).orElse(ChannelId.GLOBAL);
        EnumSet<ChannelId> muted = EnumSet.noneOf(ChannelId.class);
        try (var mutedStatement =
            database
                .connection()
                .prepareStatement("SELECT channel FROM chat_muted_channels WHERE player_id = ?")) {
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
          connection.prepareStatement(
              "INSERT INTO chat_preferences(player_id, active_channel, updated_at) VALUES (?, ?, ?) "
                  + "ON CONFLICT(player_id) DO UPDATE SET active_channel=excluded.active_channel, updated_at=excluded.updated_at")) {
        statement.setBytes(1, SqliteConnection.uuidToBytes(preferences.playerId()));
        statement.setString(2, preferences.activeChannel().key());
        statement.setLong(3, System.currentTimeMillis());
        statement.executeUpdate();
      }
      try (var delete =
          connection.prepareStatement("DELETE FROM chat_muted_channels WHERE player_id = ?")) {
        delete.setBytes(1, SqliteConnection.uuidToBytes(preferences.playerId()));
        delete.executeUpdate();
      }
      try (var insert =
          connection.prepareStatement(
              "INSERT INTO chat_muted_channels(player_id, channel) VALUES (?, ?)")) {
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
