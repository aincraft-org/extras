package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.ChannelId;
import dev.mintychochip.api.ChannelPreferences;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteChatRepositoryTest {

  @TempDir Path tempDir;

  @Test
  void preferencesSurviveRepositoryReopen() {
    Path file = tempDir.resolve("chat.db");
    UUID playerId = UUID.randomUUID();
    ChannelPreferences expected =
        new ChannelPreferences(playerId, ChannelId.PARTY, Set.of(ChannelId.LOCAL, ChannelId.LFG));

    SqliteChatRepository first = new SqliteChatRepository(file);
    first.save(expected);
    first.close();

    SqliteChatRepository second = new SqliteChatRepository(file);
    assertEquals(java.util.Optional.of(expected), second.load(playerId));
    second.close();
  }

  @Test
  void normalizedSchemaUsesSeparateMutedRowsAndCascades() throws Exception {
    Path file = tempDir.resolve("schema.db");
    SqliteChatRepository repository = new SqliteChatRepository(file);
    repository.close();

    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        var columns =
            connection.createStatement().executeQuery("PRAGMA table_info(chat_preferences)")) {
      Set<String> names = new java.util.HashSet<>();
      while (columns.next()) {
        names.add(columns.getString("name"));
      }
      assertEquals(Set.of("player_id", "active_channel", "updated_at"), names);
    }
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        var statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute(
          "INSERT INTO chat_preferences(player_id, active_channel, updated_at) VALUES (X'00000000000000000000000000000001', 'global', 1)");
      statement.execute(
          "INSERT INTO chat_muted_channels(player_id, channel) VALUES (X'00000000000000000000000000000001', 'local')");
      statement.execute(
          "DELETE FROM chat_preferences WHERE player_id = X'00000000000000000000000000000001'");
      try (var result = statement.executeQuery("SELECT COUNT(*) FROM chat_muted_channels")) {
        result.next();
        assertEquals(0, result.getInt(1));
      }
    }
  }

  @Test
  void missingPreferencesAreAbsent() {
    SqliteChatRepository repository = new SqliteChatRepository(tempDir.resolve("chat.db"));

    assertTrue(repository.load(UUID.randomUUID()).isEmpty());
    repository.close();
  }
}
