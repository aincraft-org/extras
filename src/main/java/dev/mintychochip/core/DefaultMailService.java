package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;
import dev.mintychochip.api.MailService;
import dev.mintychochip.api.MailboxView;
import dev.mintychochip.api.SendMailResult;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Paper-free {@link MailService} backed by a {@link MailRepository}. Owns all validation so every
 * caller gets the same rules. Events are published after the persistence operation succeeded, only
 * for actual state changes: a no-op mark, an unknown-id operation, and a zero-row bulk delete emit
 * nothing; {@link #deleteAllRead(UUID)} emits one event per actually deleted mail.
 */
public final class DefaultMailService implements MailService {

  /** Maximum body length after trimming. */
  static final int MAX_BODY_LENGTH = 2000;

  private final MailRepository repository;
  private final Clock clock;
  private final InProcessExtrasEventService eventService;

  public DefaultMailService(MailRepository repository) {
    this(repository, Clock.systemUTC(), InProcessExtrasEventService.noOp());
  }

  public DefaultMailService(
      MailRepository repository, Clock clock, InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
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
    MailMessage sent =
        repository.insert(
            new MailMessage(
                0,
                recipient,
                trimmedSender,
                trimmedBody,
                clock.instant().toEpochMilli(),
                false,
                attachment));
    eventService.publish(
        new ExtrasEvent.MailSent(
            UUID.randomUUID(), clock.instant(), sent.id(), senderId, recipient));
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
    if (repository.markRead(recipient, mailId)) {
      eventService.publish(
          new ExtrasEvent.MailRead(UUID.randomUUID(), clock.instant(), recipient, mailId));
    }
  }

  @Override
  public void markUnread(UUID recipient, long mailId) {
    if (repository.markUnread(recipient, mailId)) {
      eventService.publish(
          new ExtrasEvent.MailUnread(UUID.randomUUID(), clock.instant(), recipient, mailId));
    }
  }

  @Override
  public Optional<String> claimAttachment(UUID recipient, long mailId) {
    Optional<String> blob = repository.claim(recipient, mailId);
    if (blob.isPresent()) {
      eventService.publish(
          new ExtrasEvent.MailAttachmentClaimed(
              UUID.randomUUID(), clock.instant(), recipient, mailId));
    }
    return blob;
  }

  @Override
  public boolean delete(UUID recipient, long mailId) {
    boolean deleted = repository.delete(recipient, mailId);
    if (deleted) {
      eventService.publish(
          new ExtrasEvent.MailDeleted(UUID.randomUUID(), clock.instant(), recipient, mailId));
    }
    return deleted;
  }

  @Override
  public int deleteAllRead(UUID recipient) {
    List<Long> deletedIds = repository.deletedIdsAllRead(recipient);
    for (Long mailId : deletedIds) {
      eventService.publish(
          new ExtrasEvent.MailDeleted(UUID.randomUUID(), clock.instant(), recipient, mailId));
    }
    return deletedIds.size();
  }

  @Override
  public int unreadCount(UUID recipient) {
    return repository.unreadCount(recipient);
  }
}
