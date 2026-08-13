package dev.mintychochip.core;

import dev.mintychochip.api.TradeResult;
import dev.mintychochip.api.TradeService;
import dev.mintychochip.api.TradeSnapshot;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory, synchronized implementation for transient two-player trades.
 *
 * <p>Successful lifecycle transitions publish typed events after the in-memory mutation; failed
 * operations (unknown requests, non-participants, stale offers, already-confirmed) emit nothing.
 */
public final class DefaultTradeService implements TradeService {
  private final Clock clock;
  private final InProcessExtrasEventService eventService;
  private final Object lock = new Object();
  private final Map<UUID, UUID> incomingRequests = new HashMap<>();
  private final Map<UUID, TradeState> tradesByPlayer = new HashMap<>();

  public DefaultTradeService() {
    this(Clock.systemUTC(), InProcessExtrasEventService.noOp());
  }

  public DefaultTradeService(Clock clock, InProcessExtrasEventService eventService) {
    this.clock = java.util.Objects.requireNonNull(clock, "clock");
    this.eventService = java.util.Objects.requireNonNull(eventService, "eventService");
  }

  @Override
  public TradeResult request(UUID requesterId, UUID targetId) {
    requireIds(requesterId, targetId);
    synchronized (lock) {
      if (requesterId.equals(targetId)) {
        return TradeResult.SELF_TRADE;
      }
      if (tradesByPlayer.containsKey(requesterId) || tradesByPlayer.containsKey(targetId)) {
        return TradeResult.ALREADY_TRADING;
      }
      if (incomingRequests.containsKey(targetId)
          && requesterId.equals(incomingRequests.get(targetId))) {
        return TradeResult.REQUEST_EXISTS;
      }
      incomingRequests.put(targetId, requesterId);
      eventService.publish(
          new ExtrasEvent.TradeRequested(
              UUID.randomUUID(), clock.instant(), requesterId, targetId));
      return TradeResult.SUCCESS;
    }
  }

  @Override
  public TradeResult accept(UUID playerId) {
    requireId(playerId);
    synchronized (lock) {
      UUID requesterId = incomingRequests.remove(playerId);
      if (requesterId == null) {
        return TradeResult.NO_REQUEST;
      }
      if (tradesByPlayer.containsKey(requesterId) || tradesByPlayer.containsKey(playerId)) {
        return TradeResult.ALREADY_TRADING;
      }
      TradeState state = new TradeState(UUID.randomUUID(), requesterId, playerId);
      tradesByPlayer.put(requesterId, state);
      tradesByPlayer.put(playerId, state);
      eventService.publish(
          new ExtrasEvent.TradeAccepted(
              UUID.randomUUID(), clock.instant(), state.tradeId, requesterId, playerId));
      return TradeResult.SUCCESS;
    }
  }

  @Override
  public TradeResult decline(UUID playerId) {
    requireId(playerId);
    synchronized (lock) {
      UUID requesterId = incomingRequests.remove(playerId);
      if (requesterId == null) {
        return TradeResult.NO_REQUEST;
      }
      eventService.publish(
          new ExtrasEvent.TradeDeclined(UUID.randomUUID(), clock.instant(), requesterId, playerId));
      return TradeResult.SUCCESS;
    }
  }

  @Override
  public TradeResult cancel(UUID playerId) {
    requireId(playerId);
    synchronized (lock) {
      TradeState state = tradesByPlayer.get(playerId);
      if (state == null) {
        return TradeResult.NOT_PARTICIPANT;
      }
      remove(state);
      eventService.publish(
          new ExtrasEvent.TradeCancelled(
              UUID.randomUUID(), clock.instant(), state.tradeId, playerId));
      return TradeResult.SUCCESS;
    }
  }

  @Override
  public TradeResult confirm(UUID playerId, long offerVersion) {
    requireId(playerId);
    synchronized (lock) {
      TradeState state = tradesByPlayer.get(playerId);
      if (state == null) {
        return TradeResult.NOT_PARTICIPANT;
      }
      if (state.versionOf(playerId) != offerVersion) {
        return TradeResult.STALE_OFFER;
      }
      if (state.confirmedOf(playerId)) {
        return TradeResult.ALREADY_CONFIRMED;
      }
      state.setConfirmed(playerId, true);
      return TradeResult.SUCCESS;
    }
  }

  @Override
  public TradeResult complete(UUID playerId, long firstOfferVersion, long secondOfferVersion) {
    requireId(playerId);
    synchronized (lock) {
      TradeState state = tradesByPlayer.get(playerId);
      if (state == null) {
        return TradeResult.ALREADY_COMPLETED;
      }
      if (state.firstVersion != firstOfferVersion || state.secondVersion != secondOfferVersion) {
        return TradeResult.STALE_OFFER;
      }
      if (!state.firstConfirmed || !state.secondConfirmed) {
        return TradeResult.NOT_CONFIRMED;
      }
      remove(state);
      eventService.publish(
          new ExtrasEvent.TradeCompleted(
              UUID.randomUUID(),
              clock.instant(),
              state.tradeId,
              state.firstPlayerId,
              state.secondPlayerId));
      return TradeResult.SUCCESS;
    }
  }

  @Override
  public void offerChanged(UUID playerId) {
    requireId(playerId);
    synchronized (lock) {
      TradeState state = tradesByPlayer.get(playerId);
      if (state != null) {
        state.incrementVersion(playerId);
        state.firstConfirmed = false;
        state.secondConfirmed = false;
      }
    }
  }

  @Override
  public Optional<UUID> pendingRequestFrom(UUID playerId) {
    requireId(playerId);
    synchronized (lock) {
      return Optional.ofNullable(incomingRequests.get(playerId));
    }
  }

  @Override
  public Optional<TradeSnapshot> tradeOf(UUID playerId) {
    requireId(playerId);
    synchronized (lock) {
      TradeState state = tradesByPlayer.get(playerId);
      return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }
  }

  private void remove(TradeState state) {
    tradesByPlayer.remove(state.firstPlayerId);
    tradesByPlayer.remove(state.secondPlayerId);
  }

  private static void requireIds(UUID first, UUID second) {
    requireId(first);
    requireId(second);
  }

  private static void requireId(UUID id) {
    if (id == null) {
      throw new NullPointerException("player id");
    }
  }

  private static final class TradeState {
    private final UUID tradeId;
    private final UUID firstPlayerId;
    private final UUID secondPlayerId;
    private long firstVersion;
    private long secondVersion;
    private boolean firstConfirmed;
    private boolean secondConfirmed;

    private TradeState(UUID tradeId, UUID firstPlayerId, UUID secondPlayerId) {
      this.tradeId = tradeId;
      this.firstPlayerId = firstPlayerId;
      this.secondPlayerId = secondPlayerId;
    }

    private long versionOf(UUID playerId) {
      return firstPlayerId.equals(playerId) ? firstVersion : secondVersion;
    }

    private boolean confirmedOf(UUID playerId) {
      return firstPlayerId.equals(playerId) ? firstConfirmed : secondConfirmed;
    }

    private void setConfirmed(UUID playerId, boolean value) {
      if (firstPlayerId.equals(playerId)) {
        firstConfirmed = value;
      } else {
        secondConfirmed = value;
      }
    }

    private void incrementVersion(UUID playerId) {
      if (firstPlayerId.equals(playerId)) {
        firstVersion++;
      } else {
        secondVersion++;
      }
    }

    private TradeSnapshot snapshot() {
      return new TradeSnapshot(
          tradeId,
          firstPlayerId,
          secondPlayerId,
          firstVersion,
          secondVersion,
          firstConfirmed,
          secondConfirmed);
    }
  }
}
