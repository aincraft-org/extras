package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Result of an attempted daily reward claim, including the payout descriptor on success. */
public record DailyRewardClaim(DailyRewardResult result, Reward reward) {

  public DailyRewardClaim {
    Objects.requireNonNull(result, "result");
    if (result == DailyRewardResult.CLAIMED) {
      Objects.requireNonNull(reward, "reward");
    }
  }

  public static DailyRewardClaim of(DailyRewardResult result) {
    return new DailyRewardClaim(result, null);
  }

  public static DailyRewardClaim claimed(Reward reward) {
    return new DailyRewardClaim(DailyRewardResult.CLAIMED, reward);
  }
}
