package dev.mintychochip.core;

import dev.mintychochip.api.FriendRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for friend requests and mutual friendships.
 *
 * <p>Every mutation is atomic. {@link #close()} releases the backing store. Requests are stored
 * directionally ({@code requester}, {@code target}); friendships are stored canonically ({@code
 * playerA} &lt; {@code playerB}) so each unordered pair has exactly one row.
 */
interface FriendRepository extends AutoCloseable {

  Optional<Instant> findRequest(UUID requesterId, UUID targetId);

  List<FriendRequest> findIncoming(UUID targetId);

  List<FriendRequest> findOutgoing(UUID requesterId);

  Optional<Instant> findFriendship(UUID playerA, UUID playerB);

  List<UUID> findFriendIds(UUID playerId);

  void upsertRequest(UUID requesterId, UUID targetId, Instant createdAt);

  void deleteRequest(UUID requesterId, UUID targetId);

  void addFriendship(UUID playerA, UUID playerB, Instant since);

  void deleteFriendship(UUID playerA, UUID playerB);

  @Override
  void close();
}
