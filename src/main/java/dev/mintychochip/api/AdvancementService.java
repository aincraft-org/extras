package dev.mintychochip.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public surface for a catalog of custom advancements and per-player completions.
 *
 * <p>Operators and downstream consumers define catalog entries; only known ids can be granted.
 * Completions persist independently of Bukkit. A successful first-time grant publishes a committed
 * domain event after the store write.
 */
public interface AdvancementService {

  /** Adds or replaces a catalog entry so downstream plugins can define custom advancements. */
  void register(CustomAdvancement advancement);

  /** Returns the catalog entry for {@code advancementId}, if defined. */
  Optional<CustomAdvancement> find(String advancementId);

  /** Snapshot of the current catalog, in registration order. */
  Collection<CustomAdvancement> catalog();

  /**
   * Records completion of {@code advancementId} for {@code playerId}. Unknown ids are rejected and
   * do not persist. Repeat grants of a completed id are idempotent.
   */
  AdvancementResult grant(UUID playerId, String advancementId);

  /** Whether {@code playerId} has completed {@code advancementId}. */
  boolean has(UUID playerId, String advancementId);

  /** Completed advancement ids for {@code playerId} (empty for unknown players). */
  Set<String> completed(UUID playerId);
}
