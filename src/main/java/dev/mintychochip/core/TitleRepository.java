package dev.mintychochip.core;

import dev.mintychochip.api.TitleProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for per-player title state.
 *
 * <p>Titles are persisted as one document per player; {@link #close()} is a
 * no-op for file-backed stores but exists for symmetry with the other domains.
 */
interface TitleRepository extends AutoCloseable {

    Optional<TitleProfile> findById(UUID playerId);

    void save(TitleProfile profile);

    @Override
    void close();
}
