package dev.mintychochip.api.events;

/**
 * Idempotent cancellation handle for an {@link ExtrasEventService} subscription.
 *
 * <p>{@link #close()} stops future deliveries to the subscription and is safe to call more than
 * once. Subscriptions are non-durable: closing the event service invalidates all of them.
 */
public interface EventSubscription extends AutoCloseable {

  /** Cancels the subscription; repeated calls are no-ops. */
  @Override
  void close();
}
