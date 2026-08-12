package dev.mintychochip.api;

/** Outcome of {@link MailService#send}. */
public enum SendMailResult {
  /** Mail was delivered to the recipient's mailbox. */
  SUCCESS,
  /** Body was blank (after trim) or longer than 2000 characters. */
  INVALID_MESSAGE,
  /** Sender and recipient are the same player. */
  SELF_MAIL
}
