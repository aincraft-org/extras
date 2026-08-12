package dev.mintychochip.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public read/write surface for player mailboxes.
 *
 * <p>Implementation is Paper-free (in {@code core}); item attachments are opaque blobs so this SPI
 * stays Bukkit-free. Registered via {@code Bukkit.getServicesManager()} like the other Extras SPIs.
 */
public interface MailService {

  /**
   * Sends mail from {@code senderId} to {@code recipient}.
   *
   * @param senderId the sender's UUID (drives the self-mail check)
   * @param recipient the recipient's UUID
   * @param senderName display name stamped on the mail (trimmed)
   * @param body the message (trimmed; 1–2000 chars)
   * @param attachment opaque item blob, or {@code null} for none
   */
  SendMailResult send(
      UUID senderId, UUID recipient, String senderName, String body, String attachment);

  /**
   * Returns one page of {@code recipient}'s mailbox, newest-first. {@code pageSize} is clamped to
   * {@code [1, 200]}; {@code page} is zero-based.
   */
  MailboxView mailbox(UUID recipient, int page, int pageSize);

  /** Marks one mail as read; no-op for unknown ids. */
  void markRead(UUID recipient, long mailId);

  /** Marks one mail as unread; no-op for unknown ids. */
  void markUnread(UUID recipient, long mailId);

  /**
   * Claims the attachment of {@code mailId} for {@code recipient}. Returns empty when the mail has
   * no attachment or it is already claimed. See the spec's claim contract for crash semantics.
   */
  Optional<String> claimAttachment(UUID recipient, long mailId);

  /** Deletes one mail; returns true when a row was deleted. */
  boolean delete(UUID recipient, long mailId);

  /**
   * Deletes all READ messages for {@code recipient}; returns the count deleted. Unread/unclaimed
   * messages are kept.
   */
  int deleteAllRead(UUID recipient);

  /** Returns the recipient's unread mail count. */
  int unreadCount(UUID recipient);
}
