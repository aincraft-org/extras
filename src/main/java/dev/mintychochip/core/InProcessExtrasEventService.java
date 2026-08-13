package dev.mintychochip.core;

import dev.mintychochip.api.events.EventSubscription;
import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.events.ExtrasEventService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * In-process, thread-safe {@link ExtrasEventService} with package-private publication for core
 * domain services.
 *
 * <p>Subscriptions are stored in a copy-on-write list so iteration is safe while another thread
 * cancels. Each publication delivers to a snapshot of currently active subscriptions; a failing
 * subscriber is reported to the error handler and never blocks other subscribers. Closing the
 * service atomically deactivates and clears all subscriptions and rejects new ones. No executor or
 * background thread is created.
 */
public final class InProcessExtrasEventService implements ExtrasEventService, AutoCloseable {

  private final Object lifecycleLock = new Object();
  private final CopyOnWriteArrayList<SubscriptionImpl> subscriptions = new CopyOnWriteArrayList<>();
  private final Consumer<Throwable> errorHandler;
  private boolean closed;

  public InProcessExtrasEventService(Consumer<Throwable> errorHandler) {
    this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
  }

  /** Returns a bus with no subscribers and a silent error handler (for default constructors). */
  public static InProcessExtrasEventService noOp() {
    return new InProcessExtrasEventService(failure -> {});
  }

  @Override
  public EventSubscription subscribe(Consumer<? super ExtrasEvent> listener) {
    return register(null, Objects.requireNonNull(listener, "listener"));
  }

  @Override
  public <E extends ExtrasEvent> EventSubscription subscribe(
      Class<E> eventType, Consumer<? super E> listener) {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(listener, "listener");
    return register(eventType, event -> listener.accept(eventType.cast(event)));
  }

  private EventSubscription register(
      Class<? extends ExtrasEvent> eventType, Consumer<? super ExtrasEvent> listener) {
    synchronized (lifecycleLock) {
      if (closed) {
        throw new IllegalStateException("event service is closed");
      }
      SubscriptionImpl subscription = new SubscriptionImpl(eventType, listener);
      subscriptions.add(subscription);
      return subscription;
    }
  }

  /** Publishes {@code event} to all matching active subscriptions; no-op when closed. */
  @Override
  public void publish(ExtrasEvent event) {
    Objects.requireNonNull(event, "event");
    List<SubscriptionImpl> current;
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      current = List.copyOf(subscriptions);
    }
    for (SubscriptionImpl subscription : current) {
      subscription.deliver(event);
    }
  }

  @Override
  public void close() {
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      for (SubscriptionImpl subscription : subscriptions) {
        subscription.active.set(false);
      }
      subscriptions.clear();
    }
  }

  private final class SubscriptionImpl implements EventSubscription {
    private final Class<? extends ExtrasEvent> eventType;
    private final Consumer<? super ExtrasEvent> listener;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private SubscriptionImpl(
        Class<? extends ExtrasEvent> eventType, Consumer<? super ExtrasEvent> listener) {
      this.eventType = eventType;
      this.listener = listener;
    }

    private void deliver(ExtrasEvent event) {
      if (!active.get() || (eventType != null && !eventType.isInstance(event))) {
        return;
      }
      try {
        listener.accept(event);
      } catch (Throwable failure) {
        try {
          errorHandler.accept(failure);
        } catch (Throwable ignored) {
          // A failing error handler must not affect other subscribers.
        }
      }
    }

    @Override
    public void close() {
      if (active.compareAndSet(true, false)) {
        subscriptions.remove(this);
      }
    }
  }
}
