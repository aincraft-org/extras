package dev.mintychochip.core;

import dev.mintychochip.api.AdvancementResult;
import dev.mintychochip.api.AdvancementService;
import dev.mintychochip.api.CustomAdvancement;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default Bukkit-free {@link AdvancementService} backed by an {@link AdvancementRepository}.
 *
 * <p>Catalog mutations and grants share a single lock so check-then-act (unknown id, duplicate
 * grant) is atomic. Completions are cached per player. {@link ExtrasEvent.AdvancementGranted} is
 * published only after the store write succeeds, outside the mutation lock.
 */
public final class DefaultAdvancementService implements AdvancementService {

  private final AdvancementRepository repository;
  private final Clock clock;
  private final InProcessExtrasEventService eventService;
  private final Map<String, CustomAdvancement> catalog = new LinkedHashMap<>();
  private final ConcurrentMap<UUID, Set<String>> cache = new ConcurrentHashMap<>();
  private final Object mutationLock = new Object();

  public DefaultAdvancementService(AdvancementRepository repository) {
    this(repository, List.of(), Clock.systemUTC(), InProcessExtrasEventService.noOp());
  }

  public DefaultAdvancementService(
      AdvancementRepository repository, Collection<CustomAdvancement> catalog) {
    this(repository, catalog, Clock.systemUTC(), InProcessExtrasEventService.noOp());
  }

  public DefaultAdvancementService(
      AdvancementRepository repository,
      Collection<CustomAdvancement> catalog,
      Clock clock,
      InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
    for (CustomAdvancement advancement : Objects.requireNonNull(catalog, "catalog")) {
      this.catalog.put(advancement.id(), advancement);
    }
  }

  @Override
  public void register(CustomAdvancement advancement) {
    Objects.requireNonNull(advancement, "advancement");
    synchronized (mutationLock) {
      catalog.put(advancement.id(), advancement);
    }
  }

  @Override
  public Optional<CustomAdvancement> find(String advancementId) {
    String id = CustomAdvancement.normalizeId(advancementId);
    if (id == null) {
      return Optional.empty();
    }
    synchronized (mutationLock) {
      return Optional.ofNullable(catalog.get(id));
    }
  }

  @Override
  public Collection<CustomAdvancement> catalog() {
    synchronized (mutationLock) {
      return List.copyOf(catalog.values());
    }
  }

  @Override
  public AdvancementResult grant(UUID playerId, String advancementId) {
    Objects.requireNonNull(playerId, "playerId");
    String id = CustomAdvancement.normalizeId(advancementId);
    ExtrasEvent.AdvancementGranted granted;
    synchronized (mutationLock) {
      if (id == null || !catalog.containsKey(id)) {
        return AdvancementResult.UNKNOWN_ADVANCEMENT;
      }
      Set<String> completed = mutableCompleted(playerId);
      if (!completed.add(id)) {
        return AdvancementResult.ALREADY_COMPLETED;
      }
      repository.save(playerId, completed);
      granted =
          new ExtrasEvent.AdvancementGranted(UUID.randomUUID(), clock.instant(), playerId, id);
    }
    eventService.publish(granted);
    return AdvancementResult.GRANTED;
  }

  @Override
  public boolean has(UUID playerId, String advancementId) {
    Objects.requireNonNull(playerId, "playerId");
    String id = CustomAdvancement.normalizeId(advancementId);
    if (id == null) {
      return false;
    }
    return completed(playerId).contains(id);
  }

  @Override
  public Set<String> completed(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    synchronized (mutationLock) {
      return Set.copyOf(mutableCompleted(playerId));
    }
  }

  private Set<String> mutableCompleted(UUID playerId) {
    return cache.computeIfAbsent(playerId, id -> new LinkedHashSet<>(repository.findCompleted(id)));
  }
}
