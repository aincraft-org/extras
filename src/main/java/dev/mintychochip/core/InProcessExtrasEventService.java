package dev.mintychochip.core;

import dev.mintychochip.api.events.EventSubscription;
import dev.mintychochip.api.events.ExtrasEvent;
import dev.mintychochip.api.events.ExtrasEventService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.aincraft.api.event.Event;
import org.aincraft.api.event.EventBus;
import org.aincraft.event.EventBuses;

/** In-process {@link ExtrasEventService} backed by the Utilities domain event bus. */
public final class InProcessExtrasEventService implements ExtrasEventService, AutoCloseable {

  private final Object lifecycleLock = new Object();
  private final List<SubscriptionImpl> subscriptions = new ArrayList<>();
  private final EventBus eventBus;
  private final Consumer<Throwable> errorHandler;
  private boolean closed;

  public InProcessExtrasEventService(Consumer<Throwable> errorHandler) {
    this(EventBuses.create(), errorHandler);
  }

  InProcessExtrasEventService(EventBus eventBus, Consumer<Throwable> errorHandler) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
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
      subscription.utilitySubscription =
          eventBus.subscribe(DomainEvent.class, envelope -> subscription.deliver(envelope.event()));
      subscriptions.add(subscription);
      return subscription;
    }
  }

  /** Publishes {@code event} synchronously through the Utilities event bus. */
  @Override
  public void publish(ExtrasEvent event) {
    Objects.requireNonNull(event, "event");
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
    }
    eventBus.post(new DomainEvent(event));
  }

  @Override
  public void close() {
    List<SubscriptionImpl> current;
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      current = List.copyOf(subscriptions);
      subscriptions.clear();
    }
    current.forEach(SubscriptionImpl::close);
  }

  private final class SubscriptionImpl implements EventSubscription {
    private final Class<? extends ExtrasEvent> eventType;
    private final Consumer<? super ExtrasEvent> listener;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private org.aincraft.api.event.Subscription utilitySubscription;

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
      if (!active.compareAndSet(true, false)) {
        return;
      }
      org.aincraft.api.event.Subscription subscription = utilitySubscription;
      if (subscription != null) {
        eventBus.unsubscribe(subscription);
      }
      synchronized (lifecycleLock) {
        subscriptions.remove(this);
      }
    }
  }

  private record DomainEvent(ExtrasEvent event) implements Event {
    private DomainEvent {
      Objects.requireNonNull(event, "event");
    }
  }
}
