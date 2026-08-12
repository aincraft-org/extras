package dev.mintychochip.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public surface for a player's cosmetic titles.
 *
 * <p>Titles are arbitrary strings (no fixed catalog). A player holds an unordered set of unlocked
 * title ids and may equip at most one of them at a time. Title state is created lazily on the first
 * successful mutation; players with no stored state read as empty and accept grants.
 */
public interface TitleService {

  /**
   * Adds {@code titleId} to the player's unlocked set (after validation and trimming). Creates the
   * player's title state when absent. Persists immediately on success.
   */
  TitleResult grantTitle(UUID playerId, String titleId);

  /**
   * Removes {@code titleId} from the player's unlocked set. If the player's equipped title is
   * revoked, it is unequipped as well. Persists on success.
   */
  TitleResult revokeTitle(UUID playerId, String titleId);

  /**
   * Equips {@code titleId}, which must already be unlocked. Equipping replaces any previously
   * equipped title. Persists on success.
   */
  TitleResult equipTitle(UUID playerId, String titleId);

  /**
   * Clears the player's equipped title. Persists on success; succeeds as a no-op when nothing is
   * equipped.
   */
  TitleResult unequipTitle(UUID playerId);

  /** Returns the player's unlocked title ids (empty for unknown players). */
  Set<String> unlockedTitles(UUID playerId);

  /** Returns the player's equipped title id, if any (empty for unknown players). */
  Optional<String> equippedTitle(UUID playerId);
}
