package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import dev.mintychochip.api.events.EventSubscription;
import dev.mintychochip.api.events.ExtrasEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Subscription, filtering, cancellation, close, and subscriber-isolation behavior. */
class InProcessExtrasEventServiceTest {

  private final List<Throwable> failures = new ArrayList<>();
  private InProcessExtrasEventService bus;
  private final Instant now = Instant.parse("2026-08-12T12:00:00Z");

  @BeforeEach
  void setUp() {
    bus = new InProcessExtrasEventService(failures::add);
  }

  @Test
  void allAndTypedSubscriptionsReceiveMatchingEvents() {
    List<ExtrasEvent> all = new ArrayList<>();
    List<ExtrasEvent.TitleGranted> titles = new ArrayList<>();
    bus.subscribe(all::add);
    bus.subscribe(ExtrasEvent.TitleGranted.class, titles::add);

    ExtrasEvent.TitleGranted event = titleGranted();
    publish(event);

    assertEquals(List.of(event), all);
    assertEquals(List.of(event), titles);
  }

  @Test
  void typedSubscriptionReceivesSubclassEvents() {
    List<ExtrasEvent.TitleGranted> titles = new ArrayList<>();
    bus.subscribe(ExtrasEvent.TitleGranted.class, titles::add);
    publish(titleGranted());
    assertEquals(1, titles.size());
  }

  @Test
  void typedSubscriptionIgnoresOtherEventTypes() {
    List<ExtrasEvent.TitleGranted> titles = new ArrayList<>();
    bus.subscribe(ExtrasEvent.TitleGranted.class, titles::add);

    publish(new ExtrasEvent.TitleEquipped(UUID.randomUUID(), now, UUID.randomUUID(), "Crown"));
    publish(new ExtrasEvent.TitleUnequipped(UUID.randomUUID(), now, UUID.randomUUID(), "Crown"));

    assertEquals(0, titles.size());
  }

  @Test
  void closeIsIdempotentAndStopsDelivery() {
    AtomicInteger calls = new AtomicInteger();
    EventSubscription subscription = bus.subscribe(event -> calls.incrementAndGet());

    subscription.close();
    subscription.close();
    publish(titleGranted());

    assertEquals(0, calls.get());
  }

  @Test
  void oneThrowingSubscriberDoesNotBlockTheNextSubscriber() {
    AtomicInteger calls = new AtomicInteger();
    bus.subscribe(
        event -> {
          throw new IllegalStateException("boom");
        });
    bus.subscribe(event -> calls.incrementAndGet());

    publish(titleGranted());

    assertEquals(1, calls.get());
    assertEquals(1, failures.size());
    assertEquals("boom", failures.get(0).getMessage());
  }

  @Test
  void failingErrorHandlerDoesNotAffectOtherSubscribers() {
    bus = new InProcessExtrasEventService(failure -> fail("error handler must not throw"));
    bus.subscribe(
        event -> {
          throw new IllegalStateException("boom");
        });
    bus.subscribe(event -> fail("delivery must not be blocked by a throwing error handler"));

    publish(titleGranted());
  }

  @Test
  void publishRejectsNullEvents() {
    assertThrows(NullPointerException.class, () -> publish(null));
  }

  @Test
  void subscribeRejectsNullListenerAndType() {
    assertThrows(
        NullPointerException.class,
        () -> bus.subscribe((java.util.function.Consumer<? super ExtrasEvent>) null));
    assertThrows(
        NullPointerException.class, () -> bus.subscribe(ExtrasEvent.TitleGranted.class, null));
  }

  @Test
  void closeClearsSubscriptionsAndRejectsNewSubscriptions() {
    bus.subscribe(event -> fail("closed bus delivered an event"));
    bus.close();
    bus.close();

    assertThrows(IllegalStateException.class, () -> bus.subscribe(event -> {}));
    publish(titleGranted());
  }

  @Test
  void publishAfterCloseIsSilentNoOp() {
    bus.close();
    publish(titleGranted());
  }

  @Test
  void eachSubscriptionDeliversAtMostOncePerPublish() {
    AtomicInteger calls = new AtomicInteger();
    bus.subscribe(event -> calls.incrementAndGet());
    bus.subscribe(event -> calls.incrementAndGet());

    publish(titleGranted());

    assertEquals(2, calls.get());
  }

  private void publish(ExtrasEvent event) {
    // Package-private publish is exercised through the internal publisher capability.
    bus.publish(event);
  }

  private ExtrasEvent.TitleGranted titleGranted() {
    return new ExtrasEvent.TitleGranted(UUID.randomUUID(), now, UUID.randomUUID(), "Champion");
  }
}
