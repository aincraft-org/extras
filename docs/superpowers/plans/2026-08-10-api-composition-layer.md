# API Composition Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional service façade, typed committed-domain events, and a read-only social snapshot while preserving the four existing ModularExtras domain SPIs.

**Architecture:** Keep `FriendService`, `PartyService`, `TitleService`, and `MailService` as independent Bukkit-free contracts. Add API records/interfaces under `dev.jlo.extras.api`, an in-process event implementation under `dev.jlo.extras.core`, and inject the event implementation into existing core services so events are emitted only after persistence succeeds and outside mutation locks. Compose the façade and snapshot from the live domain services without adding a new database or command surface.

**Tech Stack:** Java 21, Paper API 1.21.11, SQLite JDBC, JUnit 5, Gradle Shadow Jar.

## Global Constraints

- Preserve all existing public methods and behavior on `FriendService`, `PartyService`, `TitleService`, and `MailService`.
- Keep every new API type under `dev.jlo.extras.api` free of Bukkit, JDBC, repository, and Paper adapter types.
- Use immutable records/value objects; `SocialSnapshot` must defensively copy `friendIds` with `Set.copyOf`.
- Publish events only after SQLite transaction commit or successful title JSON persistence.
- Publish outside `DefaultFriendService`, `DefaultPartyService`, and `DefaultTitleService` mutation locks.
- Failed operations and state-preserving no-ops emit no events.
- A bulk `MailService.deleteAllRead` emits one `MailDeleted` event per actual deleted mail ID; zero-row and retained rows emit none.
- Event delivery is in-process, synchronous on the publishing thread, non-durable, and isolated per subscriber.
- Retain the four existing individual ServicesManager registrations and register the new composition services at `ServicePriority.Normal`.
- Do not add commands, GUIs, block/privacy policy, persistent notifications, replay, an outbox, cross-domain transactions, or new gameplay domains.
- Follow the existing repository style: conventional commit messages, no formatter/linters added, and tests run with `./gradlew test`.

---

## File map

### New API files

- `src/main/java/dev/jlo/extras/api/ExtrasServices.java` — aggregate discovery façade over the four existing service instances.
- `src/main/java/dev/jlo/extras/api/ExtrasEvent.java` — sealed event contract and all initial immutable event records.
- `src/main/java/dev/jlo/extras/api/ExtrasEventService.java` — all-events and type-filtered subscription methods.
- `src/main/java/dev/jlo/extras/api/EventSubscription.java` — idempotent cancellation handle.
- `src/main/java/dev/jlo/extras/api/SocialService.java` — read-only cross-domain snapshot SPI.
- `src/main/java/dev/jlo/extras/api/SocialSnapshot.java` — immutable snapshot record with defensive copying.

### New core files

- `src/main/java/dev/jlo/extras/core/InProcessExtrasEventService.java` — thread-safe in-process subscription registry and package-private event publisher.
- `src/main/java/dev/jlo/extras/core/DefaultExtrasServices.java` — façade implementation returning the live domain service instances.
- `src/main/java/dev/jlo/extras/core/DefaultSocialService.java` — composition implementation over friend, party, and title services.

### Modified core files

- `src/main/java/dev/jlo/extras/core/DefaultFriendService.java` — inject event service and publish friend events after successful mutations.
- `src/main/java/dev/jlo/extras/core/DefaultPartyService.java` — inject event service and publish party events for command and logout mutations.
- `src/main/java/dev/jlo/extras/core/DefaultTitleService.java` — inject event service/clock and publish title events only for actual state changes.
- `src/main/java/dev/jlo/extras/core/DefaultMailService.java` — inject event service/clock and publish mail events, including per-row bulk deletion events.
- `src/main/java/dev/jlo/extras/core/MailRepository.java` — expose state-change booleans and deleted IDs needed for accurate events.
- `src/main/java/dev/jlo/extras/core/SqliteMailRepository.java` — implement changed-row reporting and transactional deleted-ID capture.
- `src/main/java/dev/jlo/extras/ExtrasPlugin.java` — construct, inject, register, and close the composition layer.

### Modified/new tests

- `src/test/java/dev/jlo/extras/core/SocialSnapshotTest.java` — record null checks and defensive-copy behavior.
- `src/test/java/dev/jlo/extras/core/InProcessExtrasEventServiceTest.java` — subscription, filtering, cancellation, close, and failure isolation.
- `src/test/java/dev/jlo/extras/core/DefaultFriendServiceTest.java` — friend event mapping and no-event failures.
- `src/test/java/dev/jlo/extras/core/DefaultPartyServiceTest.java` — party event mapping, auto-transfer, disband, and no-event failures.
- `src/test/java/dev/jlo/extras/core/TitleServiceTest.java` — title event mapping and actual-change/no-op semantics.
- `src/test/java/dev/jlo/extras/core/DefaultMailServiceTest.java` — mail events and bulk event fan-out.
- `src/test/java/dev/jlo/extras/core/SqliteMailRepositoryTest.java` — deleted-ID return contract and bulk-selection boundaries.
- `src/test/java/dev/jlo/extras/core/DefaultSocialServiceTest.java` — composed snapshot behavior.
- `src/test/java/dev/jlo/extras/core/DefaultExtrasServicesTest.java` — façade identity behavior.

---

## Task 1: Add immutable API contracts

**Files:**
- Create: `src/main/java/dev/jlo/extras/api/ExtrasServices.java`
- Create: `src/main/java/dev/jlo/extras/api/ExtrasEvent.java`
- Create: `src/main/java/dev/jlo/extras/api/ExtrasEventService.java`
- Create: `src/main/java/dev/jlo/extras/api/EventSubscription.java`
- Create: `src/main/java/dev/jlo/extras/api/SocialService.java`
- Create: `src/main/java/dev/jlo/extras/api/SocialSnapshot.java`
- Create: `src/test/java/dev/jlo/extras/core/SocialSnapshotTest.java`

**Interfaces:**
- Produces the exact public signatures consumed by Tasks 2–8.
- Does not depend on any new core implementation.

- [ ] **Step 1: Write the failing snapshot tests**

Create `SocialSnapshotTest` with these behavior checks:

```java
@Test
void constructorCopiesMutableFriendSet() {
    Set<UUID> source = new HashSet<>(Set.of(alice));
    SocialSnapshot snapshot = new SocialSnapshot(alice, Optional.empty(), source, Optional.empty());

    source.add(bob);

    assertEquals(Set.of(alice), snapshot.friendIds());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.friendIds().add(bob));
}

@Test
void constructorRejectsNullValuesAndNullFriendIds() {
    assertThrows(NullPointerException.class,
            () -> new SocialSnapshot(null, Optional.empty(), Set.of(), Optional.empty()));
    assertThrows(NullPointerException.class,
            () -> new SocialSnapshot(alice, null, Set.of(), Optional.empty()));
    assertThrows(NullPointerException.class,
            () -> new SocialSnapshot(alice, Optional.empty(), Set.of((UUID) null), Optional.empty()));
    assertThrows(NullPointerException.class,
            () -> new SocialSnapshot(alice, Optional.empty(), Set.of(), null));
}
```

Use `@TempDir` nowhere in this unit test; it is a pure value-contract test.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.SocialSnapshotTest
```

Expected: compilation failure because `SocialSnapshot` does not exist.

- [ ] **Step 3: Add the façade and snapshot contracts**

Create `ExtrasServices.java`:

```java
public interface ExtrasServices {
    FriendService friends();
    PartyService parties();
    TitleService titles();
    MailService mail();
}
```

Create `SocialService.java`:

```java
public interface SocialService {
    SocialSnapshot snapshot(UUID playerId);
}
```

Create `SocialSnapshot.java` with the executable immutability contract:

```java
public record SocialSnapshot(
        UUID playerId,
        Optional<Party> party,
        Set<UUID> friendIds,
        Optional<String> equippedTitle) {

    public SocialSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(party, "party");
        friendIds = Set.copyOf(friendIds);
        Objects.requireNonNull(equippedTitle, "equippedTitle");
    }
}
```

`Set.copyOf` must be assigned back to the record component so the accessor
cannot expose the caller's mutable set.

- [ ] **Step 4: Add the event subscription contracts**

Create `EventSubscription.java`:

```java
public interface EventSubscription extends AutoCloseable {
    @Override
    void close();
}
```

Create `ExtrasEventService.java`:

```java
public interface ExtrasEventService {
    EventSubscription subscribe(Consumer<? super ExtrasEvent> listener);

    <E extends ExtrasEvent> EventSubscription subscribe(
            Class<E> eventType,
            Consumer<? super E> listener);
}
```

Create `ExtrasEvent.java` as a sealed interface with common accessors:

```java
public sealed interface ExtrasEvent permits
        ExtrasEvent.FriendRequestCreated,
        ExtrasEvent.FriendRequestAccepted,
        ExtrasEvent.FriendRequestDeclined,
        ExtrasEvent.FriendRequestCancelled,
        ExtrasEvent.FriendshipRemoved,
        ExtrasEvent.PartyCreated,
        ExtrasEvent.PartyInviteCreated,
        ExtrasEvent.PartyInviteAccepted,
        ExtrasEvent.PartyInviteDeclined,
        ExtrasEvent.PartyMemberLeft,
        ExtrasEvent.PartyMemberKicked,
        ExtrasEvent.PartyDisbanded,
        ExtrasEvent.PartyLeadershipTransferred,
        ExtrasEvent.TitleGranted,
        ExtrasEvent.TitleRevoked,
        ExtrasEvent.TitleEquipped,
        ExtrasEvent.TitleUnequipped,
        ExtrasEvent.MailSent,
        ExtrasEvent.MailRead,
        ExtrasEvent.MailUnread,
        ExtrasEvent.MailAttachmentClaimed,
        ExtrasEvent.MailDeleted {
    UUID eventId();
    Instant occurredAt();

    record FriendRequestCreated(UUID eventId, Instant occurredAt,
                                UUID requesterId, UUID targetId) implements ExtrasEvent {}
    record FriendRequestAccepted(UUID eventId, Instant occurredAt,
                                 UUID requesterId, UUID recipientId) implements ExtrasEvent {}
    record FriendRequestDeclined(UUID eventId, Instant occurredAt,
                                 UUID requesterId, UUID recipientId) implements ExtrasEvent {}
    record FriendRequestCancelled(UUID eventId, Instant occurredAt,
                                  UUID requesterId, UUID targetId) implements ExtrasEvent {}
    record FriendshipRemoved(UUID eventId, Instant occurredAt,
                             UUID actorId, UUID targetId) implements ExtrasEvent {}

    record PartyCreated(UUID eventId, Instant occurredAt, Party party) implements ExtrasEvent {}
    record PartyInviteCreated(UUID eventId, Instant occurredAt,
                              UUID partyId, UUID inviterId, UUID inviteeId,
                              Instant expiresAt) implements ExtrasEvent {}
    record PartyInviteAccepted(UUID eventId, Instant occurredAt,
                               UUID partyId, UUID inviteeId) implements ExtrasEvent {}
    record PartyInviteDeclined(UUID eventId, Instant occurredAt,
                               UUID partyId, UUID inviteeId) implements ExtrasEvent {}
    record PartyMemberLeft(UUID eventId, Instant occurredAt,
                           UUID partyId, UUID memberId) implements ExtrasEvent {}
    record PartyMemberKicked(UUID eventId, Instant occurredAt,
                             UUID partyId, UUID actorId, UUID memberId) implements ExtrasEvent {}
    record PartyDisbanded(UUID eventId, Instant occurredAt,
                          UUID partyId, UUID leaderId,
                          List<UUID> formerMemberIds) implements ExtrasEvent {}
    record PartyLeadershipTransferred(UUID eventId, Instant occurredAt,
                                      UUID partyId, UUID oldLeaderId,
                                      UUID newLeaderId) implements ExtrasEvent {}

    record TitleGranted(UUID eventId, Instant occurredAt,
                        UUID playerId, String titleId) implements ExtrasEvent {}
    record TitleRevoked(UUID eventId, Instant occurredAt,
                        UUID playerId, String titleId) implements ExtrasEvent {}
    record TitleEquipped(UUID eventId, Instant occurredAt,
                         UUID playerId, String titleId) implements ExtrasEvent {}
    record TitleUnequipped(UUID eventId, Instant occurredAt,
                           UUID playerId, String titleId) implements ExtrasEvent {}

    record MailSent(UUID eventId, Instant occurredAt,
                    long mailId, UUID senderId, UUID recipientId) implements ExtrasEvent {}
    record MailRead(UUID eventId, Instant occurredAt,
                    UUID recipientId, long mailId) implements ExtrasEvent {}
    record MailUnread(UUID eventId, Instant occurredAt,
                      UUID recipientId, long mailId) implements ExtrasEvent {}
    record MailAttachmentClaimed(UUID eventId, Instant occurredAt,
                                 UUID recipientId, long mailId) implements ExtrasEvent {}
    record MailDeleted(UUID eventId, Instant occurredAt,
                       UUID recipientId, long mailId) implements ExtrasEvent {}
}
```

Add compact constructors to every event record. They must require non-null
UUIDs, timestamps, IDs, and text; `PartyDisbanded` must use
`List.copyOf(formerMemberIds)`. `MailSent` permits no body or attachment in the
event, keeping event payloads smaller and avoiding accidental content leakage.
`PartyCreated` must require the immutable `Party` value.

- [ ] **Step 5: Run the focused tests to verify they pass**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.SocialSnapshotTest
```

Expected: PASS.

- [ ] **Step 6: Commit the API contract unit**

```bash
git add src/main/java/dev/jlo/extras/api/ExtrasServices.java \
  src/main/java/dev/jlo/extras/api/ExtrasEvent.java \
  src/main/java/dev/jlo/extras/api/ExtrasEventService.java \
  src/main/java/dev/jlo/extras/api/EventSubscription.java \
  src/main/java/dev/jlo/extras/api/SocialService.java \
  src/main/java/dev/jlo/extras/api/SocialSnapshot.java \
  src/test/java/dev/jlo/extras/core/SocialSnapshotTest.java
git commit -m "feat: add composition API contracts"
```

---

## Task 2: Implement the in-process event service

**Files:**
- Create: `src/main/java/dev/jlo/extras/core/InProcessExtrasEventService.java`
- Create: `src/test/java/dev/jlo/extras/core/InProcessExtrasEventServiceTest.java`

**Interfaces:**
- Consumes: `ExtrasEvent`, `ExtrasEventService`, and `EventSubscription` from Task 1.
- Produces: a public `InProcessExtrasEventService` implementing the subscription API and a package-private `publish(ExtrasEvent)` method used by core services.

- [ ] **Step 1: Write failing event-service tests**

Test the following exact behaviors:

```java
@Test
void allAndTypedSubscriptionsReceiveMatchingEvents() {
    List<ExtrasEvent> all = new ArrayList<>();
    List<ExtrasEvent.TitleGranted> titles = new ArrayList<>();
    bus.subscribe(all::add);
    bus.subscribe(ExtrasEvent.TitleGranted.class, titles::add);

    ExtrasEvent.TitleGranted event = titleGranted();
    bus.publish(event);

    assertEquals(List.of(event), all);
    assertEquals(List.of(event), titles);
}

@Test
void closeIsIdempotentAndStopsDelivery() {
    AtomicInteger calls = new AtomicInteger();
    EventSubscription subscription = bus.subscribe(event -> calls.incrementAndGet());

    subscription.close();
    subscription.close();
    bus.publish(titleGranted());

    assertEquals(0, calls.get());
}

@Test
void oneThrowingSubscriberDoesNotBlockTheNextSubscriber() {
    List<Throwable> failures = new ArrayList<>();
    bus = new InProcessExtrasEventService(failures::add);
    AtomicInteger calls = new AtomicInteger();
    bus.subscribe(event -> { throw new IllegalStateException("boom"); });
    bus.subscribe(event -> calls.incrementAndGet());

    bus.publish(titleGranted());

    assertEquals(1, calls.get());
    assertEquals(1, failures.size());
}

@Test
void closeClearsSubscriptionsAndRejectsNewSubscriptions() {
    bus.subscribe(event -> fail("closed bus delivered an event"));
    bus.close();

    assertThrows(IllegalStateException.class, () -> bus.subscribe(event -> {}));
}
```

Use `InProcessExtrasEventService(Consumer<Throwable> errorHandler)` with a
`List<Throwable>` in tests. Add a `static noOp()` factory for existing domain
constructors; it must create a bus with no subscribers and a no-op error
handler.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.InProcessExtrasEventServiceTest
```

Expected: compilation failure because the implementation does not exist.

- [ ] **Step 3: Implement subscription and publication**

Use a `CopyOnWriteArrayList<SubscriptionImpl>` so subscription iteration is
safe while another thread cancels. Each subscription stores an optional
`Class<? extends ExtrasEvent>` filter and a `Consumer<? super ExtrasEvent>`
callback. The typed overload wraps the `Consumer<? super E>` with a cast-safe
adapter after checking `eventType.isInstance(event)`.

Required behavior:

```java
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class InProcessExtrasEventService implements ExtrasEventService, AutoCloseable {
    private final Object lifecycleLock = new Object();
    private final CopyOnWriteArrayList<SubscriptionImpl> subscriptions = new CopyOnWriteArrayList<>();
    private final Consumer<Throwable> errorHandler;
    private boolean closed;

    public InProcessExtrasEventService(Consumer<Throwable> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

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
            Class<? extends ExtrasEvent> eventType,
            Consumer<? super ExtrasEvent> listener) {
        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException("event service is closed");
            }
            SubscriptionImpl subscription = new SubscriptionImpl(eventType, listener);
            subscriptions.add(subscription);
            return subscription;
        }
    }

    void publish(ExtrasEvent event) {
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
                Class<? extends ExtrasEvent> eventType,
                Consumer<? super ExtrasEvent> listener) {
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
```

`publish` must reject null events, snapshot the current matching subscriptions,
and call each at most once. Catch `Throwable` around each callback, pass the
failure to `errorHandler`, and continue. `close` atomically marks the bus
closed and clears subscriptions. A subscription's `close` is idempotent and
removes only itself. The service must not start an executor or create any
background thread.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.InProcessExtrasEventServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit the event service unit**

```bash
git add src/main/java/dev/jlo/extras/core/InProcessExtrasEventService.java \
  src/test/java/dev/jlo/extras/core/InProcessExtrasEventServiceTest.java
git commit -m "feat: add in-process extras event service"
```

---

## Task 3: Emit friend events after successful mutations

**Files:**
- Modify: `src/main/java/dev/jlo/extras/core/DefaultFriendService.java`
- Modify: `src/test/java/dev/jlo/extras/core/DefaultFriendServiceTest.java`

**Interfaces:**
- Consumes: `InProcessExtrasEventService` from Task 2.
- Produces: unchanged `FriendService` behavior plus the five friend event types from Task 1.

- [ ] **Step 1: Add event collection to the existing friend tests**

Construct the service with a test event bus and subscribe to
`ExtrasEvent.class`. Add these assertions:

```java
assertEquals(FriendResult.SUCCESS, service.sendRequest(alice, bob));
assertSingleEvent(ExtrasEvent.FriendRequestCreated.class,
        event -> assertEquals(alice, event.requesterId()));
```

Cover successful `acceptRequest`, `declineRequest`, `cancelRequest`, and
`removeFriend` with their corresponding records. Add failure assertions that
`SELF_REQUEST`, `ALREADY_FRIENDS`, `REQUEST_EXISTS`, `NO_REQUEST`, and
`NOT_FRIENDS` do not append an event.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.DefaultFriendServiceTest
```

Expected: the existing behavioral tests pass, but the new event assertions fail
because no events are emitted.

- [ ] **Step 3: Add injection and publish outside the mutation lock**

Add a constructor overload while preserving the existing constructors:

```java
public DefaultFriendService(FriendRepository repository, Clock clock) {
    this(repository, clock, InProcessExtrasEventService.noOp());
}

public DefaultFriendService(
        FriendRepository repository,
        Clock clock,
        InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
}
```

For each mutation, leave failure returns unchanged inside the synchronized
section. On success, construct the event using a fresh `UUID.randomUUID()` and
`clock.instant()`, leave the synchronized section, call
`eventService.publish(event)`, then return `FriendResult.SUCCESS`.

Use this exact mapping:

| Mutation | Event fields |
|---|---|
| `sendRequest(requesterId, targetId)` | `FriendRequestCreated(requesterId, targetId)` |
| `acceptRequest(recipientId, requesterId)` | `FriendRequestAccepted(requesterId, recipientId)` |
| `declineRequest(recipientId, requesterId)` | `FriendRequestDeclined(requesterId, recipientId)` |
| `cancelRequest(requesterId, targetId)` | `FriendRequestCancelled(requesterId, targetId)` |
| `removeFriend(actorId, targetId)` | `FriendshipRemoved(actorId, targetId)` |

Do not publish from inside `synchronized (mutationLock)`. A subscriber must be
able to call `friendIdsOf` or another friend mutation without deadlocking.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.DefaultFriendServiceTest
```

Expected: PASS, including the pre-existing persistence/invariant tests.

- [ ] **Step 5: Commit the friend event unit**

```bash
git add src/main/java/dev/jlo/extras/core/DefaultFriendService.java \
  src/test/java/dev/jlo/extras/core/DefaultFriendServiceTest.java
git commit -m "feat: emit friend domain events"
```

---

## Task 4: Emit party events, including automatic leadership changes

**Files:**
- Modify: `src/main/java/dev/jlo/extras/core/DefaultPartyService.java`
- Modify: `src/test/java/dev/jlo/extras/core/DefaultPartyServiceTest.java`

**Interfaces:**
- Consumes: `InProcessExtrasEventService` from Task 2.
- Produces: unchanged `PartyService` behavior plus the eight party event types from Task 1.

- [ ] **Step 1: Add event assertions to party tests**

Use a test event bus in `DefaultPartyServiceTest` and capture events in order.
Add coverage for:

- creation → `PartyCreated` with the resulting immutable `Party`;
- a new invite → `PartyInviteCreated` with expiry;
- accepted invite → `PartyInviteAccepted`;
- declined invite → `PartyInviteDeclined`;
- non-leader leave → `PartyMemberLeft`;
- leader leave with remaining members → `PartyMemberLeft` followed by
  `PartyLeadershipTransferred`;
- solo leader leave → `PartyMemberLeft` followed by `PartyDisbanded`;
- leader kick → `PartyMemberKicked`;
- explicit disband → `PartyDisbanded` with all former member IDs;
- explicit leadership transfer → `PartyLeadershipTransferred`;
- leader logout with remaining members → only
  `PartyLeadershipTransferred` because logout evicts the cache but does not
  remove membership;
- failed results and duplicate invites → no events;
- transferring to the current leader → successful no-op with no event.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.DefaultPartyServiceTest
```

Expected: existing tests pass and new event assertions fail.

- [ ] **Step 3: Inject the event service and collect events under the lock**

Preserve both existing constructors and add these event-aware overloads:

```java
public DefaultPartyService(
        PartyRepository repository,
        InProcessExtrasEventService eventService) {
    this(repository, Clock.systemUTC(), DEFAULT_INVITE_TTL, DEFAULT_SIZE_LIMIT, eventService);
}

public DefaultPartyService(
        PartyRepository repository,
        Clock clock,
        Duration inviteTtl,
        int sizeLimit,
        InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.inviteTtl = Objects.requireNonNull(inviteTtl, "inviteTtl");
    if (sizeLimit < 1) {
        throw new IllegalArgumentException("sizeLimit must be >= 1");
    }
    this.sizeLimit = sizeLimit;
    this.eventService = Objects.requireNonNull(eventService, "eventService");
}

The existing one-argument and four-argument constructors delegate to the
event-aware overloads with `InProcessExtrasEventService.noOp()`.

For each public mutation, create a local `List<ExtrasEvent>` while holding
`mutationLock`. Repository and cache changes happen exactly as they do today.
Add events to the list only after the repository call and resulting cache state
succeed. After the synchronized block, iterate the list and call
`eventService.publish(event)` in order.

Use these precise event rules:

- `createParty`: publish `PartyCreated` carrying the newly created `Party`.
- `invite`: publish `PartyInviteCreated` only when `already` is false; an
  existing invite remains a successful no-event operation.
- `acceptInvite`: publish `PartyInviteAccepted` after the refreshed party is
  cached.
- `declineInvite`: publish `PartyInviteDeclined` after deleting the invite.
- non-leader `leaveParty`: publish `PartyMemberLeft`.
- leader `leaveParty` with remaining members: publish `PartyMemberLeft`, then
  `PartyLeadershipTransferred` from the old leader to the first remaining
  member.
- leader `leaveParty` as the final member: publish `PartyMemberLeft`, then
  `PartyDisbanded` with the former one-member list.
- `kick`: publish `PartyMemberKicked` with actor and removed member.
- `disband`: publish `PartyDisbanded` with leader and a defensive copy of the
  former membership list.
- `transferLeadership`: compare the target with the current leader before
  writing; publish only when the leader actually changes.
- `logout`: when a leader changes to the first remaining member, publish
  `PartyLeadershipTransferred`; cache-only eviction emits no event.

Refactor `leaveAsLeader` and `logout` to return or append event data rather than
publishing from inside the mutation lock. No event may be emitted for a failed
result or a cache-only operation.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.DefaultPartyServiceTest
```

Expected: PASS, including invite expiry, cap, leadership, cache, and event
coverage.

- [ ] **Step 5: Commit the party event unit**

```bash
git add src/main/java/dev/jlo/extras/core/DefaultPartyService.java \
  src/test/java/dev/jlo/extras/core/DefaultPartyServiceTest.java
git commit -m "feat: emit party domain events"
```

---

## Task 5: Emit title events only for actual title changes

**Files:**
- Modify: `src/main/java/dev/jlo/extras/core/DefaultTitleService.java`
- Modify: `src/test/java/dev/jlo/extras/core/TitleServiceTest.java`

**Interfaces:**
- Consumes: `InProcessExtrasEventService` from Task 2.
- Produces: unchanged `TitleService` behavior plus title events and deterministic timestamps.

- [ ] **Step 1: Add title event tests**

Use a mutable test `Clock`, an event bus, and an event list. Assert:

- successful grant emits `TitleGranted`;
- successful revoke emits `TitleRevoked`;
- revoking the equipped title emits `TitleRevoked` followed by
  `TitleUnequipped` carrying the revoked/equipped title;
- equipping a different title emits `TitleEquipped`;
- equipping the already equipped title remains `SUCCESS` but emits no event;
- unequipping an equipped title emits `TitleUnequipped`;
- unequipping an unknown or already unequipped player remains `SUCCESS` but
  emits no event;
- invalid, duplicate, and unowned-title results emit no event.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.TitleServiceTest
```

Expected: existing title persistence tests pass and new event assertions fail.

- [ ] **Step 3: Inject clock and event service**

Add fields for `Clock` and `InProcessExtrasEventService`. Preserve the existing
one-argument constructor by delegating to:

```java
public DefaultTitleService(TitleRepository repository) {
    this(repository, Clock.systemUTC(), InProcessExtrasEventService.noOp());
}

public DefaultTitleService(
        TitleRepository repository,
        Clock clock,
        InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
}
```

Keep all existing title validation and persistence rules. Capture the previous
equipped title before mutating. For `equipTitle`, return `SUCCESS` without
saving or publishing when the requested title equals the current equipped title.
For `unequipTitle`, save/publish only when a profile exists with a non-null
previous equipped title. For `revokeTitle`, publish `TitleRevoked` and then
`TitleUnequipped` when the revoked title was equipped. Publish all events after
leaving `mutationLock`.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.TitleServiceTest
```

Expected: PASS, including JSON round trips and no-op semantics.

- [ ] **Step 5: Commit the title event unit**

```bash
git add src/main/java/dev/jlo/extras/core/DefaultTitleService.java \
  src/test/java/dev/jlo/extras/core/TitleServiceTest.java
git commit -m "feat: emit title domain events"
```

---

## Task 6: Emit mail events and expose bulk-deleted IDs

**Files:**
- Modify: `src/main/java/dev/jlo/extras/core/MailRepository.java`
- Modify: `src/main/java/dev/jlo/extras/core/SqliteMailRepository.java`
- Modify: `src/main/java/dev/jlo/extras/core/DefaultMailService.java`
- Modify: `src/test/java/dev/jlo/extras/core/SqliteMailRepositoryTest.java`
- Modify: `src/test/java/dev/jlo/extras/core/DefaultMailServiceTest.java`

**Interfaces:**
- Consumes: `InProcessExtrasEventService` and event records from Task 2.
- Produces: unchanged public `MailService` signatures; only the package/core repository contract changes from a bulk count to deleted IDs.

- [ ] **Step 1: Add repository and service event tests**

In `SqliteMailRepositoryTest`, update bulk assertions to inspect the returned
ID list and add a boundary case with one qualifying read plain mail, one read
unclaimed attachment, and one unread mail:

```java
List<Long> deleted = repository.deleteAllRead(alice);
assertEquals(List.of(readPlain.id()), deleted);
assertEquals(2, repository.count(alice));
```

In `DefaultMailServiceTest`, subscribe to events and assert:

- `send` emits `MailSent` with generated ID, sender ID, and recipient ID;
- `markRead` emits only on unread → read;
- repeated `markRead` and unknown IDs emit no event;
- `markUnread` emits only on read → unread;
- successful attachment claim emits `MailAttachmentClaimed`, while empty claim
  emits none;
- successful single delete emits `MailDeleted`, failed delete emits none;
- `deleteAllRead` still returns an `int` count and emits one `MailDeleted` per
  returned ID, with no events for zero-row or retained messages.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.SqliteMailRepositoryTest \
  --tests dev.jlo.extras.core.DefaultMailServiceTest
```

Expected: repository compilation/test failure because the existing bulk method
returns `int` and mark methods do not report whether state changed; new event
assertions also fail.

- [ ] **Step 3: Change the package-level repository contract**

In `MailRepository.java`, change only the internal mutation return contracts:

```java
boolean markRead(UUID recipient, long mailId);
boolean markUnread(UUID recipient, long mailId);
List<Long> deleteAllRead(UUID recipient);
```

Leave `MailService.markRead`, `markUnread`, and `deleteAllRead` unchanged. The
service remains the public count-returning API.

- [ ] **Step 4: Implement accurate SQLite mutation reporting**

In `SqliteMailRepository`:

- `markRead` updates only `read = 0` rows and returns whether exactly one row
  changed.
- `markUnread` updates only `read = 1` rows and returns whether exactly one row
  changed.
- `deleteAllRead` starts one transaction, selects qualifying IDs using the
  existing rule (`recipient = ? AND read = 1 AND (attachment IS NULL OR claimed = 1)`),
  deletes those rows in the same transaction, commits, and returns IDs in stable
  ascending ID order. On SQL failure, roll back and throw the existing
  `UncheckedIOException` form.

The select and delete must be serialized by the repository's existing
`synchronized` method and no qualifying ID may be reported before commit.

- [ ] **Step 5: Inject clock and event service into `DefaultMailService`**

Preserve `DefaultMailService(MailRepository)` and delegate to a new overload:

```java
public DefaultMailService(MailRepository repository) {
    this(repository, Clock.systemUTC(), InProcessExtrasEventService.noOp());
}

public DefaultMailService(
        MailRepository repository,
        Clock clock,
        InProcessExtrasEventService eventService) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.eventService = Objects.requireNonNull(eventService, "eventService");
}
```

Use `clock.millis()` for the existing `MailMessage.sentAtMillis` value and
`clock.instant()` for event timestamps. Do not alter validation, recipient
scoping, attachment handling, pagination, or the public `deleteAllRead` result.

Emit events only when the repository reports an actual state change:

| Method | Event |
|---|---|
| `send` after `insert` | `MailSent(mailId, senderId, recipientId)` |
| `markRead` when repository returns true | `MailRead(recipientId, mailId)` |
| `markUnread` when repository returns true | `MailUnread(recipientId, mailId)` |
| `claimAttachment` when `Optional` is present | `MailAttachmentClaimed(recipientId, mailId)` |
| `delete` when repository returns true | `MailDeleted(recipientId, mailId)` |
| `deleteAllRead` for each returned ID | one `MailDeleted(recipientId, id)` per ID |

For bulk deletion, publish all events after the repository method returns and
return `deletedIds.size()`.

- [ ] **Step 6: Run the focused tests to verify they pass**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.SqliteMailRepositoryTest \
  --tests dev.jlo.extras.core.DefaultMailServiceTest
```

Expected: PASS, including claim atomicity, recipient scoping, deletion rules,
and per-row bulk events.

- [ ] **Step 7: Commit the mail event unit**

```bash
git add src/main/java/dev/jlo/extras/core/MailRepository.java \
  src/main/java/dev/jlo/extras/core/SqliteMailRepository.java \
  src/main/java/dev/jlo/extras/core/DefaultMailService.java \
  src/test/java/dev/jlo/extras/core/SqliteMailRepositoryTest.java \
  src/test/java/dev/jlo/extras/core/DefaultMailServiceTest.java
git commit -m "feat: emit mail domain events"
```

---

## Task 7: Add façade and read-only social composition

**Files:**
- Create: `src/main/java/dev/jlo/extras/core/DefaultExtrasServices.java`
- Create: `src/main/java/dev/jlo/extras/core/DefaultSocialService.java`
- Create: `src/test/java/dev/jlo/extras/core/DefaultExtrasServicesTest.java`
- Create: `src/test/java/dev/jlo/extras/core/DefaultSocialServiceTest.java`

**Interfaces:**
- Consumes: `ExtrasServices`, `SocialService`, `SocialSnapshot`, and the four existing domain interfaces.
- Produces: immutable composition services with no repository or Bukkit dependencies.

- [ ] **Step 1: Write façade and snapshot behavior tests**

`DefaultExtrasServicesTest` must construct four distinct test service instances
and assert identity, not equality:

```java
ExtrasServices services = new DefaultExtrasServices(friends, parties, titles, mail);
assertSame(friends, services.friends());
assertSame(parties, services.parties());
assertSame(titles, services.titles());
assertSame(mail, services.mail());
```

`DefaultSocialServiceTest` must use real existing core services over temporary
stores to assert that a granted/equipped title, friendship, and party are
reflected in one snapshot; an unknown UUID returns empty party, empty friend
set, and empty equipped title. Assert that mutating the source set after
snapshot construction does not change the snapshot.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.DefaultExtrasServicesTest \
  --tests dev.jlo.extras.core.DefaultSocialServiceTest
```

Expected: compilation failure because the implementations do not exist.

- [ ] **Step 3: Implement `DefaultExtrasServices`**

Create a `final` class with four non-null final fields and this constructor:

```java
public DefaultExtrasServices(
        FriendService friends,
        PartyService parties,
        TitleService titles,
        MailService mail) {
    this.friends = Objects.requireNonNull(friends, "friends");
    this.parties = Objects.requireNonNull(parties, "parties");
    this.titles = Objects.requireNonNull(titles, "titles");
    this.mail = Objects.requireNonNull(mail, "mail");
}
```

Each accessor returns its stored reference without wrapping or copying.

- [ ] **Step 4: Implement `DefaultSocialService`**

Create a `final` class with non-null `FriendService`, `PartyService`, and
`TitleService` dependencies. Implement:

```java
@Override
public SocialSnapshot snapshot(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return new SocialSnapshot(
            playerId,
            parties.partyOf(playerId),
            Set.copyOf(friends.friendIdsOf(playerId)),
            titles.equippedTitle(playerId));
}
```

Do not call `MailService`, do not cache, and do not add a cross-domain lock.
The `SocialSnapshot` constructor performs the final defensive copy.

- [ ] **Step 5: Run the focused tests to verify they pass**

Run:

```bash
./gradlew test --tests dev.jlo.extras.core.DefaultExtrasServicesTest \
  --tests dev.jlo.extras.core.DefaultSocialServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit the composition implementations**

```bash
git add src/main/java/dev/jlo/extras/core/DefaultExtrasServices.java \
  src/main/java/dev/jlo/extras/core/DefaultSocialService.java \
  src/test/java/dev/jlo/extras/core/DefaultExtrasServicesTest.java \
  src/test/java/dev/jlo/extras/core/DefaultSocialServiceTest.java
git commit -m "feat: add extras service composition"
```

---

## Task 8: Wire lifecycle construction and ServicesManager registration

**Files:**
- Modify: `src/main/java/dev/jlo/extras/ExtrasPlugin.java`
- Modify: `src/test/java/dev/jlo/extras/PluginDescriptorTest.java` only if a metadata assertion becomes necessary; do not test source text or add a fake Bukkit server.

**Interfaces:**
- Consumes: all three new API SPIs and their core implementations from Tasks 2 and 7.
- Produces: enabled plugin state with all seven services registered; disabled state closes the event bus and preserves existing repository shutdown.

- [ ] **Step 1: Add a lifecycle smoke checklist before editing**

Record the required enabled construction order in the implementation diff:

1. Create `InProcessExtrasEventService` with a logger-backed error handler.
2. Construct party, friend, title, and mail services with that event service.
3. Register the four existing domain SPIs unchanged.
4. Construct/register `ExtrasServices` and `SocialService` at normal priority.
5. Register existing listeners and commands unchanged.
6. Close the event service during disable before clearing its subscriptions.

No command or listener constructor should change unless required only to pass the
same existing domain service instances.

- [ ] **Step 2: Implement fields and enable wiring**

Add fields:

```java
private InProcessExtrasEventService eventService;
private ExtrasServices extrasServices;
private SocialService socialService;
```

Add imports for the new API/core types and `java.util.logging.Level`. After the
data directory is validated, construct:

```java
InProcessExtrasEventService events = new InProcessExtrasEventService(
        failure -> getLogger().log(Level.WARNING,
                "ModularExtras event subscriber failed", failure));
eventService = events;
```
Pass `events` to the event-aware constructors for `DefaultPartyService`,
`DefaultFriendService`, `DefaultTitleService`, and `DefaultMailService`.
The party service must use the two-argument `(repository, events)` overload so
the default invite TTL and party-size limit remain defined only by
`DefaultPartyService`.
Construct and register:

```java
extrasServices = new DefaultExtrasServices(friendService, partyService, titleService, mailService);
socialService = new DefaultSocialService(friendService, partyService, titleService);
Bukkit.getServicesManager().register(
        ExtrasServices.class, extrasServices, this, ServicePriority.Normal);
Bukkit.getServicesManager().register(
        SocialService.class, socialService, this, ServicePriority.Normal);
Bukkit.getServicesManager().register(
        ExtrasEventService.class, events, this, ServicePriority.Normal);
```

Register the event service before the composition façades so consumers resolving
services during enable see a fully initialized publisher. Keep the existing
four registrations and command registrations.

- [ ] **Step 3: Implement disable cleanup**

Keep the existing GUI/listener/repository cleanup. Add:

```java
if (eventService != null) {
    eventService.close();
    eventService = null;
}
extrasServices = null;
socialService = null;
```

Call `Bukkit.getServicesManager().unregister(this)` as the existing final
service cleanup; this removes all seven registrations owned by the plugin. Do
not close the event service before domain mutations triggered by normal shutdown
are complete; after the existing listeners and repositories have stopped, close
it before the final log.

- [ ] **Step 4: Run compilation and the existing descriptor tests**

Run:

```bash
./gradlew test --tests dev.jlo.extras.PluginDescriptorTest
```

Expected: PASS. The descriptor remains `ExtrasPlugin`-hosted and no new
permission or command metadata is required.

- [ ] **Step 5: Commit lifecycle wiring**

```bash
git add src/main/java/dev/jlo/extras/ExtrasPlugin.java \
  src/test/java/dev/jlo/extras/PluginDescriptorTest.java
git commit -m "feat: register composition services"
```

Do not stage `PluginDescriptorTest.java` if it is unchanged.

---

## Task 9: Run full verification and Paper smoke test

**Files:**
- No planned source changes. Only update tests if a failure identifies a contract gap covered by the spec.

**Interfaces:**
- Consumes the complete implementation from Tasks 1–8.
- Produces verified test/build/server evidence; no additional API surface.

- [ ] **Step 1: Run the full JUnit suite**

Run:

```bash
./gradlew test
```

Expected: all existing and new tests pass, including repository transaction,
concurrency, event, snapshot, and composition tests.

- [ ] **Step 2: Build the shaded plugin**

Run:

```bash
./gradlew build
```

Expected: `build/libs/modular-extras-1.0.0.jar` is produced and the test task
remains green.

- [ ] **Step 3: Exercise plugin enable/disable in the configured Paper server**

Run the existing Paper configuration:

```bash
./gradlew runServer
```

Observe `run/logs/latest.log` for the existing `ModularExtras enabled` message,
absence of event-service construction errors, and clean disable without
subscriber or repository shutdown exceptions. Stop the server with `CTRL-C`.

- [ ] **Step 4: Check the final logical diff before delivery**

Run:

```bash
git status --short
```

Expected: only the pre-existing unrelated worktree changes remain, with the
composition-layer implementation commits present and no generated server data
or build output staged.

---

## Plan self-review

- **Spec coverage:** Tasks 1–2 cover all new API contracts, typed/all-event subscriptions, cancellation, subscriber isolation, and non-durable in-process delivery. Tasks 3–6 cover every listed friend, party, title, and mail event, including `deleteAllRead` per-row fan-out. Task 7 covers the façade and composed snapshot with executable immutability. Task 8 covers construction, registration, and shutdown. Task 9 covers full tests, shaded build, and Paper smoke verification.
- **No placeholders:** Every task names exact files, signatures, commands, expected outcomes, event mappings, and commit messages. No `TODO`, `TBD`, or deferred implementation step is required for this slice.
- **Type consistency:** `InProcessExtrasEventService` is the injected concrete publisher used by all four core services; `ExtrasEventService` is the registered subscription-facing interface. `SocialService` consumes only the existing friend, party, and title interfaces. `MailService.deleteAllRead` remains `int` while only the package-level `MailRepository` returns `List<Long>`.
- **Boundary check:** The event implementation has no background executor, the snapshot has no persistence, the façade has no mutation logic, and plugin lifecycle remains the only Paper integration point.
