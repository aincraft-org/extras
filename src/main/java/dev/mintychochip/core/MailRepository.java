package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage surface for mailbox mail. Implementations are Paper-free.
 */
public interface MailRepository {

    /** Inserts {@code mail}, returning it with its generated id populated. */
    MailMessage insert(MailMessage mail);

    /** Returns one page of {@code recipient}'s mail, newest-first by sentAtMillis. */
    List<MailMessage> list(UUID recipient, int page, int pageSize);

    /** Total mail count for {@code recipient}. */
    int count(UUID recipient);

    /** Unread mail count for {@code recipient}. */
    int unreadCount(UUID recipient);

    /** Marks a mail read (no-op if unknown). */
    void markRead(UUID recipient, long mailId);

    /** Marks a mail unread (no-op if unknown). */
    void markUnread(UUID recipient, long mailId);

    /**
     * Atomically returns the unclaimed attachment blob for {@code mailId} and
     * marks it claimed. Empty when the mail is unknown, has no attachment, or
     * is already claimed.
     */
    Optional<String> claim(UUID recipient, long mailId);

    /** Deletes {@code mailId} for {@code recipient}; true when a row was deleted. */
    boolean delete(UUID recipient, long mailId);

    /**
     * Deletes all READ messages for {@code recipient} in one statement.
     * Returns the number of rows deleted. Unread/unclaimed messages are kept.
     */
    int deleteAllRead(UUID recipient);

    /** Releases the underlying connection/statement resources. */
    void close();
}
