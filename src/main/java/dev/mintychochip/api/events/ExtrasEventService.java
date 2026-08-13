package dev.mintychochip.api.events;

import java.util.function.Consumer;

/** Bukkit-free subscription and publication surface for typed {@link ExtrasEvent} events. */
public interface ExtrasEventService {

  /** Subscribes to every event published. */
  EventSubscription subscribe(Consumer<? super ExtrasEvent> listener);

  /** Subscribes to events of the exact type {@code eventType}. */
  <E extends ExtrasEvent> EventSubscription subscribe(
      Class<E> eventType, Consumer<? super E> listener);

  /** Publishes a non-null typed event to matching active subscribers. */
  void publish(ExtrasEvent event);
}
