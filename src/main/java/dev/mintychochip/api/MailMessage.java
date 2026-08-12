package dev.mintychochip.api;

import java.util.UUID;

/**
 * Immutable mail message as stored in a player's mailbox.
 *
 * @param id unique per-mail id (server-global, autoincrement)
 * @param recipient player UUID the mail is addressed to
 * @param senderName display name of the sender at send time
 * @param body message text (trimmed, 1–2000 chars)
 * @param sentAtMillis epoch millis when the mail was sent
 * @param read whether the recipient has read it (display state only)
 * @param attachment opaque serialized-item blob, or {@code null} if none
 */
public record MailMessage(
    long id,
    UUID recipient,
    String senderName,
    String body,
    long sentAtMillis,
    boolean read,
    String attachment) {}
