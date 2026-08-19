package dev.mintychochip.core;

import java.util.Collection;
import java.util.Optional;

/** Persistence for cinematic scene drafts. */
interface CinematicRepository extends AutoCloseable {

  void save(CinematicDraft draft);

  Optional<CinematicDraft> find(String name);

  Collection<CinematicDraft> loadAll();

  @Override
  void close();
}
