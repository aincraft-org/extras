package dev.mintychochip.api;

import java.util.Optional;
import java.util.UUID;

/** Bukkit-free lifecycle and confirmation surface for transient item trades. */
public interface TradeService {
  TradeResult request(UUID requesterId, UUID targetId);

  TradeResult accept(UUID playerId);

  TradeResult decline(UUID playerId);

  TradeResult cancel(UUID playerId);

  TradeResult confirm(UUID playerId, long offerVersion);

  TradeResult complete(UUID playerId, long firstOfferVersion, long secondOfferVersion);

  void offerChanged(UUID playerId);

  Optional<UUID> pendingRequestFrom(UUID playerId);

  Optional<TradeSnapshot> tradeOf(UUID playerId);
}
