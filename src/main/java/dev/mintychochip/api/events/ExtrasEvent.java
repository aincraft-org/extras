package dev.mintychochip.api.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed, committed domain events published by Extras services.
 *
 * <p>Every event is an immutable record carrying an event UUID and an occurrence timestamp plus
 * domain-specific values. Events are published only after the corresponding persistence operation
 * has succeeded, outside domain mutation locks. No event exposes a repository row, Bukkit object,
 * or item content; mail and trade events intentionally carry ids only.
 *
 * <p>Built-in events manifest their concrete types below. Downstream plugins may define additional
 * immutable records implementing this interface; the common accessors are {@link #eventId()} and
 * {@link #occurredAt()}.
 */
public interface ExtrasEvent {

  /** Unique id of this event instance. */
  UUID eventId();

  /** When the underlying state change occurred, from the domain clock. */
  Instant occurredAt();

  // -------------------------------------------------------------- friends

  /** A friend request was sent from {@code requesterId} to {@code targetId}. */
  record FriendRequestCreated(UUID eventId, Instant occurredAt, UUID requesterId, UUID targetId)
      implements ExtrasEvent {

    public FriendRequestCreated {
      requireIds(eventId, occurredAt, requesterId, targetId);
    }
  }

  /** {@code recipientId} accepted {@code requesterId}'s pending friend request. */
  record FriendRequestAccepted(UUID eventId, Instant occurredAt, UUID requesterId, UUID recipientId)
      implements ExtrasEvent {

    public FriendRequestAccepted {
      requireIds(eventId, occurredAt, requesterId, recipientId);
    }
  }

  /** {@code recipientId} declined {@code requesterId}'s pending friend request. */
  record FriendRequestDeclined(UUID eventId, Instant occurredAt, UUID requesterId, UUID recipientId)
      implements ExtrasEvent {

    public FriendRequestDeclined {
      requireIds(eventId, occurredAt, requesterId, recipientId);
    }
  }

  /** {@code requesterId} withdrew their pending friend request to {@code targetId}. */
  record FriendRequestCancelled(UUID eventId, Instant occurredAt, UUID requesterId, UUID targetId)
      implements ExtrasEvent {

    public FriendRequestCancelled {
      requireIds(eventId, occurredAt, requesterId, targetId);
    }
  }

  /** {@code actorId} removed their mutual friendship with {@code targetId}. */
  record FriendshipRemoved(UUID eventId, Instant occurredAt, UUID actorId, UUID targetId)
      implements ExtrasEvent {

    public FriendshipRemoved {
      requireIds(eventId, occurredAt, actorId, targetId);
    }
  }

  // --------------------------------------------------------------- parties

  /** {@code party} was created; it carries the immutable party snapshot. */
  record PartyCreated(UUID eventId, Instant occurredAt, dev.mintychochip.api.Party party)
      implements ExtrasEvent {

    public PartyCreated {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(occurredAt, "occurredAt");
      Objects.requireNonNull(party, "party");
    }
  }

  /** {@code inviterId} invited {@code inviteeId} to party {@code partyId}. */
  record PartyInviteCreated(
      UUID eventId,
      Instant occurredAt,
      UUID partyId,
      UUID inviterId,
      UUID inviteeId,
      Instant expiresAt)
      implements ExtrasEvent {

    public PartyInviteCreated {
      requireIds(eventId, occurredAt, partyId, inviterId, inviteeId);
      Objects.requireNonNull(expiresAt, "expiresAt");
    }
  }

  /** {@code inviteeId} accepted their invitation to party {@code partyId}. */
  record PartyInviteAccepted(UUID eventId, Instant occurredAt, UUID partyId, UUID inviteeId)
      implements ExtrasEvent {

    public PartyInviteAccepted {
      requireIds(eventId, occurredAt, partyId, inviteeId);
    }
  }

  /** {@code inviteeId} declined their invitation to party {@code partyId}. */
  record PartyInviteDeclined(UUID eventId, Instant occurredAt, UUID partyId, UUID inviteeId)
      implements ExtrasEvent {

    public PartyInviteDeclined {
      requireIds(eventId, occurredAt, partyId, inviteeId);
    }
  }

  /** {@code memberId} left party {@code partyId}. */
  record PartyMemberLeft(UUID eventId, Instant occurredAt, UUID partyId, UUID memberId)
      implements ExtrasEvent {

    public PartyMemberLeft {
      requireIds(eventId, occurredAt, partyId, memberId);
    }
  }

  /** {@code actorId} kicked {@code memberId} from party {@code partyId}. */
  record PartyMemberKicked(
      UUID eventId, Instant occurredAt, UUID partyId, UUID actorId, UUID memberId)
      implements ExtrasEvent {

    public PartyMemberKicked {
      requireIds(eventId, occurredAt, partyId, actorId, memberId);
    }
  }

  /** Party {@code partyId} was disbanded; {@code formerMemberIds} is an immutable copy. */
  record PartyDisbanded(
      UUID eventId, Instant occurredAt, UUID partyId, UUID leaderId, List<UUID> formerMemberIds)
      implements ExtrasEvent {

    public PartyDisbanded {
      requireIds(eventId, occurredAt, partyId, leaderId);
      formerMemberIds = List.copyOf(formerMemberIds);
    }
  }

  /** Leadership of party {@code partyId} moved from {@code oldLeaderId} to {@code newLeaderId}. */
  record PartyLeadershipTransferred(
      UUID eventId, Instant occurredAt, UUID partyId, UUID oldLeaderId, UUID newLeaderId)
      implements ExtrasEvent {

    public PartyLeadershipTransferred {
      requireIds(eventId, occurredAt, partyId, oldLeaderId, newLeaderId);
    }
  }

  // ---------------------------------------------------------------- titles

  /** {@code playerId} unlocked title {@code titleId}. */
  record TitleGranted(UUID eventId, Instant occurredAt, UUID playerId, String titleId)
      implements ExtrasEvent {

    public TitleGranted {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(titleId, "titleId");
    }
  }

  /** {@code playerId} lost title {@code titleId}. */
  record TitleRevoked(UUID eventId, Instant occurredAt, UUID playerId, String titleId)
      implements ExtrasEvent {

    public TitleRevoked {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(titleId, "titleId");
    }
  }

  /** {@code playerId} equipped title {@code titleId}. */
  record TitleEquipped(UUID eventId, Instant occurredAt, UUID playerId, String titleId)
      implements ExtrasEvent {

    public TitleEquipped {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(titleId, "titleId");
    }
  }

  /** {@code playerId} unequipped title {@code titleId} (the previously equipped title). */
  record TitleUnequipped(UUID eventId, Instant occurredAt, UUID playerId, String titleId)
      implements ExtrasEvent {

    public TitleUnequipped {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(titleId, "titleId");
    }
  }

  // ------------------------------------------------------------------ mail

  /** Mail {@code mailId} was sent from {@code senderId} to {@code recipientId}. */
  record MailSent(UUID eventId, Instant occurredAt, long mailId, UUID senderId, UUID recipientId)
      implements ExtrasEvent {

    public MailSent {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(occurredAt, "occurredAt");
      Objects.requireNonNull(senderId, "senderId");
      Objects.requireNonNull(recipientId, "recipientId");
    }
  }

  /** {@code recipientId}'s mail {@code mailId} was marked read. */
  record MailRead(UUID eventId, Instant occurredAt, UUID recipientId, long mailId)
      implements ExtrasEvent {

    public MailRead {
      requireIds(eventId, occurredAt, recipientId);
    }
  }

  /** {@code recipientId}'s mail {@code mailId} was marked unread. */
  record MailUnread(UUID eventId, Instant occurredAt, UUID recipientId, long mailId)
      implements ExtrasEvent {

    public MailUnread {
      requireIds(eventId, occurredAt, recipientId);
    }
  }

  /** {@code recipientId} claimed the attachment of mail {@code mailId}. */
  record MailAttachmentClaimed(UUID eventId, Instant occurredAt, UUID recipientId, long mailId)
      implements ExtrasEvent {

    public MailAttachmentClaimed {
      requireIds(eventId, occurredAt, recipientId);
    }
  }

  /** Mail {@code mailId} was deleted from {@code recipientId}'s mailbox. */
  record MailDeleted(UUID eventId, Instant occurredAt, UUID recipientId, long mailId)
      implements ExtrasEvent {

    public MailDeleted {
      requireIds(eventId, occurredAt, recipientId);
    }
  }

  // ----------------------------------------------------------------- chat

  /** {@code playerId} selected chat channel {@code channelKey}. */
  record ChatChannelSelected(UUID eventId, Instant occurredAt, UUID playerId, String channelKey)
      implements ExtrasEvent {

    public ChatChannelSelected {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(channelKey, "channelKey");
    }
  }

  /** {@code playerId} muted chat channel {@code channelKey}. */
  record ChatChannelMuted(UUID eventId, Instant occurredAt, UUID playerId, String channelKey)
      implements ExtrasEvent {

    public ChatChannelMuted {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(channelKey, "channelKey");
    }
  }

  /** {@code playerId} unmuted chat channel {@code channelKey}. */
  record ChatChannelUnmuted(UUID eventId, Instant occurredAt, UUID playerId, String channelKey)
      implements ExtrasEvent {

    public ChatChannelUnmuted {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(channelKey, "channelKey");
    }
  }

  // ----------------------------------------------------------------- trade

  /** {@code requesterId} requested a trade with {@code targetId}. */
  record TradeRequested(UUID eventId, Instant occurredAt, UUID requesterId, UUID targetId)
      implements ExtrasEvent {

    public TradeRequested {
      requireIds(eventId, occurredAt, requesterId, targetId);
    }
  }

  /** {@code accepteeId} accepted {@code requesterId}'s trade request (trade {@code tradeId}). */
  record TradeAccepted(
      UUID eventId, Instant occurredAt, UUID tradeId, UUID requesterId, UUID accepteeId)
      implements ExtrasEvent {

    public TradeAccepted {
      requireIds(eventId, occurredAt, tradeId, requesterId, accepteeId);
    }
  }

  /** {@code playerId} declined the pending trade request from {@code requesterId}. */
  record TradeDeclined(UUID eventId, Instant occurredAt, UUID requesterId, UUID playerId)
      implements ExtrasEvent {

    public TradeDeclined {
      requireIds(eventId, occurredAt, requesterId, playerId);
    }
  }

  /** Trade {@code tradeId} was cancelled (either player cancelled, or a player left). */
  record TradeCancelled(UUID eventId, Instant occurredAt, UUID tradeId, UUID cancelledBy)
      implements ExtrasEvent {

    public TradeCancelled {
      requireIds(eventId, occurredAt, tradeId);
      Objects.requireNonNull(cancelledBy, "cancelledBy");
    }
  }

  /** Trade {@code tradeId} between {@code firstPlayerId} and {@code secondPlayerId} completed. */
  record TradeCompleted(
      UUID eventId, Instant occurredAt, UUID tradeId, UUID firstPlayerId, UUID secondPlayerId)
      implements ExtrasEvent {

    public TradeCompleted {
      requireIds(eventId, occurredAt, tradeId, firstPlayerId, secondPlayerId);
    }
  }

  // ---------------------------------------------------------------- rewards

  /** {@code playerId} claimed the daily reward {@code rewardType} for day {@code day}. */
  record RewardClaimed(
      UUID eventId, Instant occurredAt, UUID playerId, String rewardType, String day)
      implements ExtrasEvent {

    public RewardClaimed {
      requireIds(eventId, occurredAt, playerId);
      Objects.requireNonNull(rewardType, "rewardType");
      Objects.requireNonNull(day, "day");
    }
  }

  /** {@code playerId}'s login streak changed; {@code streak} is the new streak length. */
  record LoginStreakChanged(UUID eventId, Instant occurredAt, UUID playerId, int streak)
      implements ExtrasEvent {

    public LoginStreakChanged {
      requireIds(eventId, occurredAt, playerId);
    }
  }

  private static void requireIds(Object... ids) {
    for (Object id : ids) {
      Objects.requireNonNull(id, "event id must not be null");
    }
  }
}
