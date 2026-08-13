package dev.mintychochip.core;

import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.rewards.Criterion;
import dev.mintychochip.api.rewards.CriterionProgress;
import dev.mintychochip.api.rewards.CriterionSnapshot;
import dev.mintychochip.api.rewards.DailyRewardClaim;
import dev.mintychochip.api.rewards.DailyRewardResult;
import dev.mintychochip.api.rewards.DailyRewardService;
import dev.mintychochip.api.rewards.DailyRewardStatus;
import dev.mintychochip.api.rewards.GainXpCriterion;
import dev.mintychochip.api.rewards.Reward;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Default persistent implementation of daily criterion progress and claims. */
public final class DefaultDailyRewardService implements DailyRewardService {

  private final SqliteRewardStore store;
  private final Clock clock;
  private final DailyWindow window;
  private final Criterion defaultCriterion;
  private final List<Criterion> fallbackPool;
  private final DefaultLeaderboardService leaderboardService;
  private final InProcessExtrasEventService eventService;
  private final Object mutationLock = new Object();

  public DefaultDailyRewardService(SqliteRewardStore store, Clock clock) {
    this(
        store,
        clock,
        new GainXpCriterion("default-xp", "Earn 1 XP", 1, Reward.xp(1)),
        List.of(),
        null,
        InProcessExtrasEventService.noOp());
  }

  public DefaultDailyRewardService(
      SqliteRewardStore store, Clock clock, Criterion defaultCriterion) {
    this(store, clock, defaultCriterion, List.of(), null, InProcessExtrasEventService.noOp());
  }

  public DefaultDailyRewardService(
      SqliteRewardStore store,
      Clock clock,
      Criterion defaultCriterion,
      InProcessExtrasEventService eventService) {
    this(store, clock, defaultCriterion, List.of(), null, eventService);
  }

  public DefaultDailyRewardService(
      SqliteRewardStore store,
      Clock clock,
      Criterion defaultCriterion,
      List<Criterion> fallbackPool) {
    this(store, clock, defaultCriterion, fallbackPool, null, InProcessExtrasEventService.noOp());
  }

  public DefaultDailyRewardService(
      SqliteRewardStore store,
      Clock clock,
      Criterion defaultCriterion,
      List<Criterion> fallbackPool,
      DefaultLeaderboardService leaderboardService) {
    this(
        store,
        clock,
        defaultCriterion,
        fallbackPool,
        leaderboardService,
        InProcessExtrasEventService.noOp());
  }

  public DefaultDailyRewardService(
      SqliteRewardStore store,
      Clock clock,
      Criterion defaultCriterion,
      List<Criterion> fallbackPool,
      DefaultLeaderboardService leaderboardService,
      InProcessExtrasEventService eventService) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.window = new DailyWindow(clock);
    this.defaultCriterion = Objects.requireNonNull(defaultCriterion, "defaultCriterion");
    this.fallbackPool = List.copyOf(fallbackPool);
    this.leaderboardService = leaderboardService;
    this.eventService = Objects.requireNonNull(eventService, "eventService");
  }

  @Override
  public CriterionSnapshot activeCriterion() {
    synchronized (mutationLock) {
      return activeCriterionLocked(fallbackPool);
    }
  }

  @Override
  public DailyRewardStatus status(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    synchronized (mutationLock) {
      CriterionSnapshot snapshot = activeCriterionLocked(fallbackPool);
      SqliteRewardStore.ProgressRow progress =
          store.findProgress(playerId, snapshot.day().toString(), snapshot.criterion().id());
      return new DailyRewardStatus(
          snapshot,
          progress.amount(),
          progress.amount() >= snapshot.criterion().target(),
          progress.claimed());
    }
  }

  @Override
  public DailyRewardResult recordProgress(UUID playerId, CriterionProgress progress) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(progress, "progress");
    synchronized (mutationLock) {
      CriterionSnapshot snapshot = activeCriterionLocked(fallbackPool);
      Criterion criterion = snapshot.criterion();
      if (!criterion.matches(progress)) {
        return DailyRewardResult.IGNORED;
      }
      SqliteRewardStore.ProgressRow existing =
          store.findProgress(playerId, snapshot.day().toString(), criterion.id());
      int amount = Math.min(criterion.target(), existing.amount() + progress.amount());
      store.saveProgress(
          playerId, snapshot.day().toString(), criterion.id(), amount, existing.claimed());
      if (leaderboardService != null) {
        leaderboardService.recordProgress(playerId, progress.amount());
      }
      return amount >= criterion.target()
          ? DailyRewardResult.COMPLETED
          : DailyRewardResult.PROGRESSED;
    }
  }

  @Override
  public DailyRewardClaim claim(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    synchronized (mutationLock) {
      CriterionSnapshot snapshot = activeCriterionLocked(fallbackPool);
      SqliteRewardStore.ProgressRow existing =
          store.findProgress(playerId, snapshot.day().toString(), snapshot.criterion().id());
      if (existing.claimed()) {
        return DailyRewardClaim.of(DailyRewardResult.ALREADY_CLAIMED);
      }
      if (existing.amount() < snapshot.criterion().target()) {
        return DailyRewardClaim.of(DailyRewardResult.NOT_CLAIMABLE);
      }
      store.saveProgress(
          playerId, snapshot.day().toString(), snapshot.criterion().id(), existing.amount(), true);
      eventService.publish(
          new ExtrasEvent.RewardClaimed(
              UUID.randomUUID(),
              clock.instant(),
              playerId,
              rewardTypeKey(snapshot.criterion()),
              snapshot.day().toString()));
      return DailyRewardClaim.claimed(snapshot.criterion().reward());
    }
  }

  /** Stable lowercase reward-type key used by the {@link ExtrasEvent.RewardClaimed} event. */
  private static String rewardTypeKey(Criterion criterion) {
    return criterion.reward().type().name().toLowerCase(Locale.ROOT);
  }

  @Override
  public DailyRewardResult forceCriterion(Criterion criterion) {
    Objects.requireNonNull(criterion, "criterion");
    synchronized (mutationLock) {
      store.saveCriterion(new CriterionSnapshot(window.date(), criterion));
      return DailyRewardResult.ROTATED;
    }
  }

  @Override
  public DailyRewardResult rotate(List<Criterion> fallbackPool) {
    Objects.requireNonNull(fallbackPool, "fallbackPool");
    synchronized (mutationLock) {
      activeCriterionLocked(fallbackPool);
      return DailyRewardResult.ROTATED;
    }
  }

  @Override
  public void close() {
    store.close();
  }

  private CriterionSnapshot activeCriterionLocked(List<Criterion> pool) {
    String dayKey = window.dateKey();
    return store
        .findCriterion(dayKey)
        .orElseGet(
            () -> {
              int index =
                  pool.isEmpty() ? 0 : Math.floorMod(window.date().toEpochDay(), pool.size());
              Criterion criterion = pool.isEmpty() ? defaultCriterion : pool.get(index);
              CriterionSnapshot snapshot = new CriterionSnapshot(window.date(), criterion);
              store.saveCriterion(snapshot);
              return snapshot;
            });
  }
}
