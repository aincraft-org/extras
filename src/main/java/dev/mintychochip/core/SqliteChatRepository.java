package dev.mintychochip.core;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite-backed persistent chat preferences using the Utilities SQL lifecycle. */
public final class SqliteChatRepository implements ChatRepository {

  private final SqliteConnection database;

  public SqliteChatRepository(Path file) {
    this.database =
        new SqliteConnection("jdbc:sqlite:" + file.toAbsolutePath(), "classpath:db/migration/chat");
  }

  @Override
  public synchronized Optional<ChannelPreferences> load(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    try {
      return database.withHandle(
          handle -> {
            Optional<ChannelId> active =
                handle
                    .createQuery(SqlStatements.load("chat/select-active.sql"))
                    .bind(0, SqliteConnection.uuidToBytes(playerId))
                    .map(
                        (row, context) ->
                            ChannelId.parse(row.getString("active_channel"))
                                .orElse(ChannelId.GLOBAL))
                    .findOne();
            return active.map(
                selected -> {
                  EnumSet<ChannelId> muted = EnumSet.noneOf(ChannelId.class);
                  handle
                      .createQuery(SqlStatements.load("chat/select-muted.sql"))
                      .bind(0, SqliteConnection.uuidToBytes(playerId))
                      .map((row, context) -> row.getString("channel"))
                      .list()
                      .forEach(channel -> ChannelId.parse(channel).ifPresent(muted::add));
                  return new ChannelPreferences(playerId, selected, muted);
                });
          });
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to load chat preferences", e);
    }
  }

  @Override
  public synchronized void save(ChannelPreferences preferences) {
    Objects.requireNonNull(preferences, "preferences");
    try {
      database.useTransaction(
          handle -> {
            handle
                .createUpdate(SqlStatements.load("chat/upsert-preferences.sql"))
                .bind(0, SqliteConnection.uuidToBytes(preferences.playerId()))
                .bind(1, preferences.activeChannel().key())
                .bind(2, System.currentTimeMillis())
                .execute();
            handle
                .createUpdate(SqlStatements.load("chat/delete-muted.sql"))
                .bind(0, SqliteConnection.uuidToBytes(preferences.playerId()))
                .execute();
            if (!preferences.mutedChannels().isEmpty()) {
              var batch = handle.prepareBatch(SqlStatements.load("chat/insert-muted.sql"));
              for (ChannelId channel : preferences.mutedChannels()) {
                batch
                    .bind(0, SqliteConnection.uuidToBytes(preferences.playerId()))
                    .bind(1, channel.key())
                    .add();
              }
              batch.execute();
            }
          });
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to save chat preferences", e);
    }
  }

  @Override
  public void close() {
    database.close();
  }
}
