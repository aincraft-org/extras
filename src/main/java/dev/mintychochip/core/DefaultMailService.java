package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;
import dev.mintychochip.api.MailService;
import dev.mintychochip.api.MailboxView;
import dev.mintychochip.api.SendMailResult;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Paper-free {@link MailService} backed by a {@link MailRepository}. Owns all validation so every
 * caller gets the same rules.
 */
public final class DefaultMailService implements MailService {

  /** Maximum body length after trimming. */
  static final int MAX_BODY_LENGTH = 2000;

  private final MailRepository repository;

  public DefaultMailService(MailRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public SendMailResult send(
      UUID senderId, UUID recipient, String senderName, String body, String attachment) {
    Objects.requireNonNull(senderId, "senderId");
    Objects.requireNonNull(recipient, "recipient");

    String trimmedBody = body == null ? "" : body.trim();
    if (trimmedBody.isEmpty() || trimmedBody.length() > MAX_BODY_LENGTH) {
      return SendMailResult.INVALID_MESSAGE;
    }
    if (senderId.equals(recipient)) {
      return SendMailResult.SELF_MAIL;
    }

    String trimmedSender = senderName == null ? "" : senderName.trim();
    repository.insert(
        new MailMessage(
            0,
            recipient,
            trimmedSender,
            trimmedBody,
            System.currentTimeMillis(),
            false,
            attachment));
    return SendMailResult.SUCCESS;
  }

  @Override
  public MailboxView mailbox(UUID recipient, int page, int pageSize) {
    Objects.requireNonNull(recipient, "recipient");
    int effectivePage = Math.max(0, page);
    int effectivePageSize = Math.max(1, Math.min(200, pageSize));
    java.util.List<MailMessage> messages =
        repository.list(recipient, effectivePage, effectivePageSize);
    return new MailboxView(
        repository.count(recipient),
        repository.unreadCount(recipient),
        effectivePage,
        effectivePageSize,
        messages);
  }

  @Override
  public void markRead(UUID recipient, long mailId) {
    repository.markRead(recipient, mailId);
  }

  @Override
  public void markUnread(UUID recipient, long mailId) {
    repository.markUnread(recipient, mailId);
  }

  @Override
  public Optional<String> claimAttachment(UUID recipient, long mailId) {
    return repository.claim(recipient, mailId);
  }

  @Override
  public boolean delete(UUID recipient, long mailId) {
    return repository.delete(recipient, mailId);
  }

  @Override
  public int deleteAllRead(UUID recipient) {
    return repository.deleteAllRead(recipient);
  }

  @Override
  public int unreadCount(UUID recipient) {
    return repository.unreadCount(recipient);
  }
}
