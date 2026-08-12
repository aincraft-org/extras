package dev.mintychochip.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One page of a player's mailbox, newest-first by send time. Immutable. */
public final class MailboxView {

  private final int total;
  private final int unreadCount;
  private final int page;
  private final int pageSize;
  private final List<MailMessage> messages;

  public MailboxView(
      int total, int unreadCount, int page, int pageSize, List<MailMessage> messages) {
    this.total = total;
    this.unreadCount = unreadCount;
    this.page = page;
    this.pageSize = pageSize;
    this.messages =
        Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(messages, "messages")));
  }

  /** Total mail count for the recipient. */
  public int total() {
    return total;
  }

  /** Unread mail count for the recipient. */
  public int unreadCount() {
    return unreadCount;
  }

  /** Zero-based page index. */
  public int page() {
    return page;
  }

  /** Requested page size (clamped to 1–200 by the service). */
  public int pageSize() {
    return pageSize;
  }

  /** This page's messages, newest-first. Immutable. */
  public List<MailMessage> messages() {
    return messages;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MailboxView other)) {
      return false;
    }
    return total == other.total
        && unreadCount == other.unreadCount
        && page == other.page
        && pageSize == other.pageSize
        && messages.equals(other.messages);
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(total);
    result = 31 * result + Integer.hashCode(unreadCount);
    result = 31 * result + Integer.hashCode(page);
    result = 31 * result + Integer.hashCode(pageSize);
    result = 31 * result + messages.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MailboxView{total="
        + total
        + ", unreadCount="
        + unreadCount
        + ", page="
        + page
        + ", pageSize="
        + pageSize
        + ", messages="
        + messages
        + '}';
  }
}
