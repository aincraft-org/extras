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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jdbi.v3.core.Handle;

/** SQLite persistence for all reward, streak, and leaderboard state. */
public final class SqliteRewardStore implements AutoCloseable {

  private final SqliteConnection sqlite;

  public SqliteRewardStore(Path databaseFile) {
    Objects.requireNonNull(databaseFile, "databaseFile");
    this.sqlite =
        new SqliteConnection(
            "jdbc:sqlite:" + databaseFile.toAbsolutePath(), "classpath:db/migration/reward");
  }

  Optional<CriterionSnapshot> findCriterion(String day) {
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("reward/select-criterion.sql"))
                  .bind(0, day)
                  .map((row, context) -> mapCriterion(row))
                  .findOne());
    } catch (RuntimeException e) {
      throw failure("read criterion " + day, e);
    }
  }

  void saveCriterion(CriterionSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    Criterion criterion = snapshot.criterion();
    CriterionKey key = CriterionKey.from(criterion);
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("reward/upsert-criterion.sql"))
                .bind(0, snapshot.day().toString())
                .bind(1, criterion.id())
                .bind(2, criterion.kind().name())
                .bind(3, key.value())
                .bind(4, criterion.target())
                .bind(5, criterion.description())
                .bind(6, criterion.reward().type().name())
                .bind(7, criterion.reward().payload())
                .bind(8, criterion.reward().amount())
                .execute());
  }

  ProgressRow findProgress(UUID playerId, String day, String criterionId) {
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("reward/select-progress.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(playerId))
                  .bind(1, day)
                  .bind(2, criterionId)
                  .map(
                      (row, context) ->
                          new ProgressRow(row.getInt("amount"), row.getInt("claimed") != 0))
                  .findOne()
                  .orElseGet(() -> new ProgressRow(0, false)));
    } catch (RuntimeException e) {
      throw failure("read progress for " + playerId, e);
    }
  }

  void saveProgress(UUID playerId, String day, String criterionId, int amount, boolean claimed) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("reward/upsert-progress.sql"))
                .bind(0, SqliteConnection.uuidToBytes(playerId))
                .bind(1, day)
                .bind(2, criterionId)
                .bind(3, amount)
                .bind(4, claimed ? 1 : 0)
                .execute());
  }

  List<LeaderboardRow> leaderboard(String period, String windowKey, int limit) {
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("reward/select-leaderboard.sql"))
                  .bind(0, period)
                  .bind(1, windowKey)
                  .bind(2, limit)
                  .map(
                      (row, context) ->
                          new LeaderboardRow(
                              SqliteConnection.uuidFromBytes(row.getBytes("player_id")),
                              row.getLong("total"),
                              row.getLong("updated_at")))
                  .list());
    } catch (RuntimeException e) {
      throw failure("read leaderboard " + period + "/" + windowKey, e);
    }
  }

  void addLeaderboard(UUID playerId, String period, String windowKey, long amount, Instant now) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("reward/add-leaderboard.sql"))
                .bind(0, SqliteConnection.uuidToBytes(playerId))
                .bind(1, period)
                .bind(2, windowKey)
                .bind(3, amount)
                .bind(4, now.toEpochMilli())
                .execute());
  }

  Optional<StreakRow> findStreak(UUID playerId) {
    try {
      return sqlite.withHandle(
          handle ->
              handle
                  .createQuery(SqlStatements.load("reward/select-streak.sql"))
                  .bind(0, SqliteConnection.uuidToBytes(playerId))
                  .map(
                      (row, context) ->
                          new StreakRow(
                              row.getInt("current_streak"),
                              row.getInt("best_streak"),
                              LocalDate.parse(row.getString("last_login_date"))))
                  .findOne());
    } catch (RuntimeException e) {
      throw failure("read streak for " + playerId, e);
    }
  }

  void saveStreak(UUID playerId, int current, int best, LocalDate lastLogin) {
    transaction(
        handle ->
            handle
                .createUpdate(SqlStatements.load("reward/upsert-streak.sql"))
                .bind(0, SqliteConnection.uuidToBytes(playerId))
                .bind(1, current)
                .bind(2, best)
                .bind(3, lastLogin.toString())
                .execute());
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

  private void transaction(Consumer<Handle> action) {
    try {
      sqlite.useTransaction(action);
    } catch (RuntimeException e) {
      throw failure("transaction", e);
    }
  }

  private static IllegalStateException failure(String action, RuntimeException exception) {
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
}
