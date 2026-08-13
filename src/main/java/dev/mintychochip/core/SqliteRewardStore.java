package dev.mintychochip.core;

import dev.mintychochip.api.rewards.CraftItemsCriterion;
import dev.mintychochip.api.rewards.Criterion;
import dev.mintychochip.api.rewards.CriterionKind;
import dev.mintychochip.api.rewards.CriterionSnapshot;
import dev.mintychochip.api.rewards.GainXpCriterion;
import dev.mintychochip.api.rewards.KillEntitiesCriterion;
import dev.mintychochip.api.rewards.LoginDaysCriterion;
import dev.mintychochip.api.rewards.MaterialKey;
import dev.mintychochip.api.rewards.MineBlocksCriterion;
import dev.mintychochip.api.rewards.PlayTimeCriterion;
import dev.mintychochip.api.rewards.Reward;
import dev.mintychochip.api.rewards.RewardType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite persistence for all reward, streak, and leaderboard state. */
public final class SqliteRewardStore implements AutoCloseable {

  private static final String[] SCHEMA = {
    """
    CREATE TABLE IF NOT EXISTS daily_criteria (
      day TEXT PRIMARY KEY,
      criterion_id TEXT NOT NULL,
      kind TEXT NOT NULL,
      key_value TEXT,
      target INTEGER NOT NULL,
      description TEXT NOT NULL,
      reward_type TEXT NOT NULL,
      reward_payload TEXT NOT NULL,
      reward_amount INTEGER NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS daily_progress (
      player_id BLOB NOT NULL,
      day TEXT NOT NULL,
      criterion_id TEXT NOT NULL,
      amount INTEGER NOT NULL,
      claimed INTEGER NOT NULL,
      PRIMARY KEY (player_id, day, criterion_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS streaks (
      player_id BLOB PRIMARY KEY,
      current_streak INTEGER NOT NULL,
      best_streak INTEGER NOT NULL,
      last_login_date TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS leaderboard_totals (
      player_id BLOB NOT NULL,
      period TEXT NOT NULL,
      window_key TEXT NOT NULL,
      total INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      PRIMARY KEY (player_id, period, window_key)
    )
    """
  };

  private final SqliteConnection sqlite;

  public SqliteRewardStore(Path databaseFile) {
    Objects.requireNonNull(databaseFile, "databaseFile");
    this.sqlite = new SqliteConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath(), SCHEMA);
  }

  Connection connection() {
    return sqlite.connection();
  }

  Optional<CriterionSnapshot> findCriterion(String day) {
    try (PreparedStatement statement =
        connection().prepareStatement("SELECT * FROM daily_criteria WHERE day = ?")) {
      statement.setString(1, day);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(mapCriterion(result)) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw failure("read criterion " + day, exception);
    }
  }

  void saveCriterion(CriterionSnapshot snapshot) {
    Criterion criterion = snapshot.criterion();
    CriterionKey key = CriterionKey.from(criterion);
    inTransaction(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT OR REPLACE INTO daily_criteria "
                      + "(day, criterion_id, kind, key_value, target, description, reward_type, "
                      + "reward_payload, reward_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, snapshot.day().toString());
            statement.setString(2, criterion.id());
            statement.setString(3, criterion.kind().name());
            statement.setString(4, key.value());
            statement.setInt(5, criterion.target());
            statement.setString(6, criterion.description());
            statement.setString(7, criterion.reward().type().name());
            statement.setString(8, criterion.reward().payload());
            statement.setInt(9, criterion.reward().amount());
            statement.executeUpdate();
          }
          return null;
        });
  }

  ProgressRow findProgress(UUID playerId, String day, String criterionId) {
    try (PreparedStatement statement =
        connection()
            .prepareStatement(
                "SELECT amount, claimed FROM daily_progress "
                    + "WHERE player_id = ? AND day = ? AND criterion_id = ?")) {
      statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
      statement.setString(2, day);
      statement.setString(3, criterionId);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return new ProgressRow(0, false);
        }
        return new ProgressRow(result.getInt("amount"), result.getInt("claimed") != 0);
      }
    } catch (SQLException exception) {
      throw failure("read progress for " + playerId, exception);
    }
  }

  void saveProgress(UUID playerId, String day, String criterionId, int amount, boolean claimed) {
    inTransaction(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT OR REPLACE INTO daily_progress "
                      + "(player_id, day, criterion_id, amount, claimed) VALUES (?, ?, ?, ?, ?)")) {
            statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
            statement.setString(2, day);
            statement.setString(3, criterionId);
            statement.setInt(4, amount);
            statement.setInt(5, claimed ? 1 : 0);
            statement.executeUpdate();
          }
          return null;
        });
  }

  List<LeaderboardRow> leaderboard(String period, String windowKey, int limit) {
    List<LeaderboardRow> rows = new ArrayList<>();
    try (PreparedStatement statement =
        connection()
            .prepareStatement(
                "SELECT player_id, total, updated_at FROM leaderboard_totals "
                    + "WHERE period = ? AND window_key = ? ORDER BY total DESC, updated_at ASC, player_id LIMIT ?")) {
      statement.setString(1, period);
      statement.setString(2, windowKey);
      statement.setInt(3, limit);
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          rows.add(
              new LeaderboardRow(
                  SqliteConnection.uuidFromBytes(result.getBytes("player_id")),
                  result.getLong("total"),
                  result.getLong("updated_at")));
        }
      }
      return rows;
    } catch (SQLException exception) {
      throw failure("read leaderboard " + period + "/" + windowKey, exception);
    }
  }

  void addLeaderboard(UUID playerId, String period, String windowKey, long amount, Instant now) {
    inTransaction(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO leaderboard_totals (player_id, period, window_key, total, updated_at) "
                      + "VALUES (?, ?, ?, ?, ?) ON CONFLICT(player_id, period, window_key) DO UPDATE SET "
                      + "total = total + excluded.total, updated_at = excluded.updated_at")) {
            statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
            statement.setString(2, period);
            statement.setString(3, windowKey);
            statement.setLong(4, amount);
            statement.setLong(5, now.toEpochMilli());
            statement.executeUpdate();
          }
          return null;
        });
  }

  Optional<StreakRow> findStreak(UUID playerId) {
    try (PreparedStatement statement =
        connection()
            .prepareStatement(
                "SELECT current_streak, best_streak, last_login_date FROM streaks WHERE player_id = ?")) {
      statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new StreakRow(
                result.getInt("current_streak"),
                result.getInt("best_streak"),
                LocalDate.parse(result.getString("last_login_date"))));
      }
    } catch (SQLException exception) {
      throw failure("read streak for " + playerId, exception);
    }
  }

  void saveStreak(UUID playerId, int current, int best, LocalDate lastLogin) {
    inTransaction(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT OR REPLACE INTO streaks "
                      + "(player_id, current_streak, best_streak, last_login_date) VALUES (?, ?, ?, ?)")) {
            statement.setBytes(1, SqliteConnection.uuidToBytes(playerId));
            statement.setInt(2, current);
            statement.setInt(3, best);
            statement.setString(4, lastLogin.toString());
            statement.executeUpdate();
          }
          return null;
        });
  }

  private CriterionSnapshot mapCriterion(ResultSet result) throws SQLException {
    CriterionKind kind = CriterionKind.valueOf(result.getString("kind"));
    String key = result.getString("key_value");
    Reward reward =
        new Reward(
            RewardType.valueOf(result.getString("reward_type")),
            result.getString("reward_payload"),
            result.getInt("reward_amount"));
    String id = result.getString("criterion_id");
    String description = result.getString("description");
    int target = result.getInt("target");
    Criterion criterion =
        switch (kind) {
          case MINE_BLOCKS ->
              new MineBlocksCriterion(id, description, MaterialKey.parse(key), target, reward);
          case KILL_ENTITIES ->
              new KillEntitiesCriterion(id, description, MaterialKey.parse(key), target, reward);
          case CRAFT_ITEMS ->
              new CraftItemsCriterion(id, description, MaterialKey.parse(key), target, reward);
          case GAIN_XP -> new GainXpCriterion(id, description, target, reward);
          case LOGIN_DAYS -> new LoginDaysCriterion(id, description, target, reward);
          case PLAY_TIME -> new PlayTimeCriterion(id, description, target, reward);
        };
    return new CriterionSnapshot(LocalDate.parse(result.getString("day")), criterion);
  }

  private void inTransaction(TransactionAction action) {
    Connection connection = connection();
    try {
      boolean autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        action.run(connection);
        connection.commit();
      } catch (SQLException | RuntimeException exception) {
        connection.rollback();
        throw exception;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    } catch (SQLException exception) {
      throw failure("transaction", exception);
    }
  }

  private static IllegalStateException failure(String action, SQLException exception) {
    return new IllegalStateException("Failed to " + action, exception);
  }

  @Override
  public void close() {
    sqlite.close();
  }

  record ProgressRow(int amount, boolean claimed) {}

  record StreakRow(int current, int best, LocalDate lastLogin) {}

  record LeaderboardRow(UUID playerId, long total, long updatedAt) {}

  private record CriterionKey(String value) {
    static CriterionKey from(Criterion criterion) {
      return switch (criterion) {
        case MineBlocksCriterion value -> new CriterionKey(value.block().toString());
        case KillEntitiesCriterion value -> new CriterionKey(value.entity().toString());
        case CraftItemsCriterion value -> new CriterionKey(value.item().toString());
        case GainXpCriterion ignored -> new CriterionKey("xp");
        case LoginDaysCriterion ignored -> new CriterionKey("login");
        case PlayTimeCriterion ignored -> new CriterionKey("seconds");
      };
    }
  }

  @FunctionalInterface
  private interface TransactionAction {
    Void run(Connection connection) throws SQLException;
  }
}
