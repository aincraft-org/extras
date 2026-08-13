package dev.mintychochip.api.rewards;

import java.util.Objects;

/** Immutable reward descriptor executed by the Paper adapter after a successful claim. */
public record Reward(RewardType type, String payload, int amount) {

  public Reward {
    Objects.requireNonNull(type, "type");
    payload = Objects.requireNonNull(payload, "payload").trim();
    if (payload.isEmpty()) {
      throw new IllegalArgumentException("payload must not be blank");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
  }

  public static Reward item(MaterialKey item, int count) {
    return new Reward(RewardType.ITEM, item.toString(), count);
  }

  public static Reward xp(int amount) {
    return new Reward(RewardType.XP, "xp", amount);
  }

  public static Reward command(String command) {
    return new Reward(RewardType.COMMAND, command, 1);
  }
}
