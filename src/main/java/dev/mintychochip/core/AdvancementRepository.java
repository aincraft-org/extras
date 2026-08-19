package dev.mintychochip.core;

import java.util.Set;
import java.util.UUID;

/**
 * Persistence port for per-player custom-advancement completions.
 *
 * <p>Each player is one document of completed advancement ids. {@link #close()} is a no-op for
 * file-backed stores but exists for symmetry with the other domains.
 */
interface AdvancementRepository extends AutoCloseable {

  /** Completed ids for {@code playerId}, or an empty set when no document exists. */
  Set<String> findCompleted(UUID playerId);

  /** Replaces the completed set for {@code playerId}. */
  void save(UUID playerId, Set<String> completed);

  @Override
  void close();
}
