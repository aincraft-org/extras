package dev.mintychochip.api;

import java.util.List;
import java.util.UUID;

/**
 * Public read/write surface for persistent player friendships.
 *
 * <p>Friendships are mutual and require accepting a pending request.
 * Requests are directional and never expire; the recipient accepts or
 * declines, the requester cancels. Friendships persist across restarts via
 * SQLite. All read views are immutable snapshots.
 */
public interface FriendService {

    /** Sends a request from {@code requesterId} to {@code targetId}. */
    FriendResult sendRequest(UUID requesterId, UUID targetId);

    /** Accepts the pending request sent by {@code requesterId} to {@code recipientId}. */
    FriendResult acceptRequest(UUID recipientId, UUID requesterId);

    /** Declines the pending request sent by {@code requesterId} to {@code recipientId}. */
    FriendResult declineRequest(UUID recipientId, UUID requesterId);

    /** Withdraws the requester's own pending request to {@code targetId}. */
    FriendResult cancelRequest(UUID requesterId, UUID targetId);

    /** Removes the mutual friendship between {@code actorId} and {@code targetId}. */
    FriendResult removeFriend(UUID actorId, UUID targetId);

    boolean areFriends(UUID a, UUID b);

    /** Requests sent to {@code playerId} that await its decision. */
    List<FriendRequest> incomingRequests(UUID playerId);

    /** Requests {@code playerId} sent that await the target's decision. */
    List<FriendRequest> outgoingRequests(UUID playerId);

    /** Friend UUIDs of {@code playerId}, unordered. */
    List<UUID> friendIdsOf(UUID playerId);
}
