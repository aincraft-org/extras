package dev.mintychochip.api.rewards;

import java.util.List;
import java.util.UUID;

/** Public API for the active daily criterion, progress, and idempotent claims. */
public interface DailyRewardService extends AutoCloseable {

  CriterionSnapshot activeCriterion();

  DailyRewardStatus status(UUID playerId);

  DailyRewardResult recordProgress(UUID playerId, CriterionProgress progress);

  DailyRewardClaim claim(UUID playerId);

  DailyRewardResult forceCriterion(Criterion criterion);

  DailyRewardResult rotate(List<Criterion> fallbackPool);

  @Override
  void close();
}
