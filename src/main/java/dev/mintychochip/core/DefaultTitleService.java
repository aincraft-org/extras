package dev.mintychochip.core;

import dev.mintychochip.api.TitleProfile;
import dev.mintychochip.api.TitleResult;
import dev.mintychochip.api.TitleService;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default Bukkit-free {@link TitleService} backed by a {@link TitleRepository}.
 *
 * <p>Every mutation is guarded by a single internal lock so check-then-act invariants (duplicate
 * grants, equip-of-unowned) are atomic with respect to concurrent callers. Reads hit a cache first,
 * then the store. Title state is created lazily on the first successful mutation. Events are
 * published only after the JSON write succeeded, for real state changes; failed and no-op mutations
 * emit nothing.
 */
public final class DefaultTitleService implements TitleService {

  private static final int MAX_TITLE_LENGTH = 64;

  private final TitleRepository repository;
  private final Clock clock;
  private final InProcessExtrasEventService eventService;
  private final ConcurrentMap<UUID, MutableTitleProfile> cache = new ConcurrentHashMap<>();
  private final Object mutationLock = new Object();

  public DefaultTitleService(TitleRepository repository) {
    this(repository, Clock.systemUTC(), InProcessExtrasEventService.noOp());
  }

  public DefaultTitleService(
      TitleRepository repository, Clock clock, InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
  }

  @Override
  public TitleResult grantTitle(UUID playerId, String titleId) {
    Objects.requireNonNull(playerId, "playerId");
    String trimmed = validateTitleId(titleId);
    if (trimmed == null) {
      return TitleResult.INVALID_TITLE;
    }
    synchronized (mutationLock) {
      MutableTitleProfile profile = getOrCreateMutable(playerId);
      if (!profile.grantTitle(trimmed)) {
        return TitleResult.ALREADY_UNLOCKED;
      }
      save(playerId);
      eventService.publish(
          new ExtrasEvent.TitleGranted(UUID.randomUUID(), clock.instant(), playerId, trimmed));
      return TitleResult.SUCCESS;
    }
  }

  @Override
  public TitleResult revokeTitle(UUID playerId, String titleId) {
    Objects.requireNonNull(playerId, "playerId");
    String trimmed = validateTitleId(titleId);
    if (trimmed == null) {
      return TitleResult.INVALID_TITLE;
    }
    synchronized (mutationLock) {
      MutableTitleProfile profile = findMutable(playerId);
      if (profile == null || !profile.revokeTitle(trimmed)) {
        return TitleResult.NOT_UNLOCKED;
      }
      save(playerId);
      eventService.publish(
          new ExtrasEvent.TitleRevoked(UUID.randomUUID(), clock.instant(), playerId, trimmed));
      return TitleResult.SUCCESS;
    }
  }

  @Override
  public TitleResult equipTitle(UUID playerId, String titleId) {
    Objects.requireNonNull(playerId, "playerId");
    String trimmed = validateTitleId(titleId);
    if (trimmed == null) {
      return TitleResult.INVALID_TITLE;
    }
    synchronized (mutationLock) {
      MutableTitleProfile profile = findMutable(playerId);
      if (profile == null || !profile.unlockedTitles().contains(trimmed)) {
        return TitleResult.NOT_UNLOCKED;
      }
      profile.equipTitle(trimmed);
      save(playerId);
      eventService.publish(
          new ExtrasEvent.TitleEquipped(UUID.randomUUID(), clock.instant(), playerId, trimmed));
      return TitleResult.SUCCESS;
    }
  }

  @Override
  public TitleResult unequipTitle(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    synchronized (mutationLock) {
      MutableTitleProfile profile = findMutable(playerId);
      if (profile != null && profile.equippedTitle() != null) {
        String previouslyEquipped = profile.equippedTitle();
        profile.unequipTitle();
        save(playerId);
        eventService.publish(
            new ExtrasEvent.TitleUnequipped(
                UUID.randomUUID(), clock.instant(), playerId, previouslyEquipped));
      }
      // Nothing equipped (no state / already unequipped) is success.
      return TitleResult.SUCCESS;
    }
  }

  @Override
  public Set<String> unlockedTitles(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    MutableTitleProfile profile = findMutable(playerId);
    return profile == null ? Collections.emptySet() : profile.unlockedTitles();
  }

  @Override
  public Optional<String> equippedTitle(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    MutableTitleProfile profile = findMutable(playerId);
    return profile == null ? Optional.empty() : Optional.ofNullable(profile.equippedTitle());
  }

  private void save(UUID playerId) {
    MutableTitleProfile profile = cache.get(playerId);
    if (profile != null) {
      repository.save(profile.toSnapshot());
    }
  }

  /** Loads {@code playerId}'s state, or creates a new in-memory profile when absent. */
  private MutableTitleProfile getOrCreateMutable(UUID playerId) {
    return cache.computeIfAbsent(
        playerId,
        id ->
            repository
                .findById(id)
                .map(MutableTitleProfile::fromSnapshot)
                .orElseGet(() -> new MutableTitleProfile(id)));
  }

  /** Returns the in-memory or store-backed profile for {@code playerId}, or {@code null}. */
  private MutableTitleProfile findMutable(UUID playerId) {
    MutableTitleProfile cached = cache.get(playerId);
    if (cached != null) {
      return cached;
    }
    return repository
        .findById(playerId)
        .map(
            profile -> {
              MutableTitleProfile loaded = MutableTitleProfile.fromSnapshot(profile);
              cache.put(playerId, loaded);
              return loaded;
            })
        .orElse(null);
  }

  /**
   * Validates and normalizes a title id: trims, rejects blank/oversized (over 64 chars) and
   * control-character ids. Returns the trimmed id, or {@code null} when invalid.
   */
  private static String validateTitleId(String titleId) {
    if (titleId == null) {
      return null;
    }
    String trimmed = titleId.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
      return null;
    }
    for (int i = 0; i < trimmed.length(); i++) {
      if (Character.isISOControl(trimmed.charAt(i))) {
        return null;
      }
    }
    return trimmed;
  }

  /**
   * Mutable in-memory title state; {@link #toSnapshot()} produces the immutable value handed to
   * callers and the repository.
   */
  private static final class MutableTitleProfile {

    private final UUID playerId;
    private final Set<String> unlockedTitles = new LinkedHashSet<>();
    private String equippedTitle;

    MutableTitleProfile(UUID playerId) {
      this.playerId = Objects.requireNonNull(playerId, "playerId");
    }

    static MutableTitleProfile fromSnapshot(TitleProfile snapshot) {
      MutableTitleProfile profile = new MutableTitleProfile(snapshot.playerId());
      profile.unlockedTitles.addAll(snapshot.unlockedTitles());
      snapshot.equippedTitle().ifPresent(title -> profile.equippedTitle = title);
      return profile;
    }

    boolean grantTitle(String titleId) {
      return unlockedTitles.add(titleId);
    }

    boolean revokeTitle(String titleId) {
      boolean removed = unlockedTitles.remove(titleId);
      if (removed && titleId.equals(equippedTitle)) {
        equippedTitle = null;
      }
      return removed;
    }

    void equipTitle(String titleId) {
      equippedTitle = titleId;
    }

    void unequipTitle() {
      equippedTitle = null;
    }

    Set<String> unlockedTitles() {
      return Collections.unmodifiableSet(unlockedTitles);
    }

    String equippedTitle() {
      return equippedTitle;
    }

    TitleProfile toSnapshot() {
      return new TitleProfile(playerId, unlockedTitles, equippedTitle);
    }
  }
}
