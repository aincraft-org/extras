# ModularExtras API Composition Layer Design

## Goal

Extend ModularExtras as an API platform without replacing its existing domain
SPIs. Downstream plugins should be able to discover the complete service set,
consume committed domain changes through typed events, and read a small
cross-domain social snapshot without depending on Paper classes or internal
repositories.

## Context

ModularExtras currently exposes four Bukkit-free service interfaces through
Paper's `ServicesManager`:

- `FriendService` for persistent requests and mutual friendships.
- `PartyService` for persistent party membership and invitations.
- `TitleService` for standalone cosmetic title state.
- `MailService` for recipient-scoped mail and opaque item attachments.

Each domain has its own immutable API values, core implementation, persistence,
and Paper command/listener adapter. `ExtrasPlugin` owns construction,
registration, and shutdown. The existing domain contracts should remain
independently usable by consumers that need only one capability.

## Design decisions

### 1. Preserve individual domain SPIs

`FriendService`, `PartyService`, `TitleService`, and `MailService` remain the
primary contracts. No existing method is removed or renamed, and no consumer is
required to depend on a new aggregate interface.

Add an optional Bukkit-free `ExtrasServices` façade:

```java
public interface ExtrasServices {
    FriendService friends();
    PartyService parties();
    TitleService titles();
    MailService mail();
}
```

The façade returns the same live service instances already registered
individually. It is a discovery/convenience surface, not a second implementation
or a replacement service locator. `ExtrasPlugin` registers it at the same
normal service priority as the existing SPIs.

### 2. Add typed committed-domain events

Add an `ExtrasEventService` SPI that allows downstream plugins to subscribe to
immutable, Bukkit-free domain events. Events are typed records implementing a
sealed `ExtrasEvent` contract rather than generic maps or string payloads.

The public subscription surface is:

```java
public interface ExtrasEventService {
    EventSubscription subscribe(Consumer<? super ExtrasEvent> listener);

    <E extends ExtrasEvent> EventSubscription subscribe(
            Class<E> eventType,
            Consumer<? super E> listener);
}

public interface EventSubscription extends AutoCloseable {
    @Override
    void close();
}
```

The all-events overload is an intentional aggregate stream. The `Class<E>`
overload provides type-filtered, compile-time-typed subscriptions.

The initial event set covers externally meaningful state changes in the four
existing domains. The concrete record names are:

- `FriendRequestCreated`, `FriendRequestAccepted`, `FriendRequestDeclined`,
  `FriendRequestCancelled`, and `FriendshipRemoved`;
- `PartyCreated`, `PartyInviteCreated`, `PartyInviteAccepted`,
  `PartyInviteDeclined`, `PartyMemberLeft`, `PartyMemberKicked`,
  `PartyDisbanded`, and `PartyLeadershipTransferred`;
- `TitleGranted`, `TitleRevoked`, `TitleEquipped`, and `TitleUnequipped`;
- `MailSent`, `MailRead`, `MailUnread`, `MailAttachmentClaimed`, and
  `MailDeleted`.
For `MailService.deleteAllRead`, the public return value remains the deleted
row count, but the event contract is per row: the repository captures the
deleted mail IDs inside the same transaction, and the publisher emits one
`MailDeleted` event for each actual deleted ID after commit. A zero-row bulk
delete emits no event. Unread messages and any other rows retained by the
existing deletion rule emit no event.

Every event contains an event UUID and an `Instant` occurrence timestamp plus
its domain-specific UUIDs and values. Events must be explicit records with
stable fields; no event exposes a repository row or Bukkit object.

Events are published only after the corresponding persistence operation has
succeeded: SQLite-backed domains publish after transaction commit, and titles
publish after the JSON write succeeds. A failed operation and a state-preserving
no-op produce no event. Each emitted event has one publisher invocation. A
single-row state change emits one event; a bulk operation may emit one event per
affected row, as `deleteAllRead` does above. For each invocation, the in-process
service makes at most one delivery attempt to each matching active subscription;
it provides no durable or exactly-once delivery guarantee across crashes,
retries, or subscriber exceptions. There is no replay or persisted outbox in
this first slice.

Subscriber callbacks run synchronously on the publishing thread. The API does
not promise a Bukkit thread, and consumers that touch Bukkit must schedule their
own work. A failing subscriber is isolated from the domain mutation and from
other subscribers. Publishing occurs outside the domain mutation lock so a
subscriber cannot block or re-enter the repository transaction.

The public subscription API exposes registration and cancellation only; domain
services receive a separate internal publisher capability so consumers cannot
forge events.

### 3. Add a read-only social composition SPI

Add `SocialService` and immutable `SocialSnapshot` values:

```java
public interface SocialService {
    SocialSnapshot snapshot(UUID playerId);
}

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

`DefaultSocialService` composes the existing services and owns no persistence.
The snapshot contains only small relationship and presentation state. Mailbox
contents, pagination, attachment claiming, and deletion remain exclusively
behind `MailService` because they have different privacy and mutation
semantics.

The canonical record constructor copies `friendIds` with `Set.copyOf`, so every
returned snapshot owns an immutable set and rejects null elements. The snapshot
and its other values are immutable. It is a composed point-in-time view, not a
cross-database transaction: the constituent services may change while the
snapshot is being assembled. The contract documents this explicitly and makes
no atomicity promise across friends, parties, and titles.

### 4. Keep the API layer Bukkit-free

All new interfaces, records, enums, and event types live under
`dev.jlo.extras.api`. They use Java standard-library types such as `UUID`,
`Instant`, `Optional`, immutable collections, and `AutoCloseable` subscription
handles. They do not reference `Player`, `JavaPlugin`, Bukkit events,
`ItemStack`, JDBC types, or repository implementations.

The Paper adapter remains responsible for ServicesManager registration,
logging subscriber failures, and lifecycle cleanup. Core services remain
responsible for persistence and state invariants.

## Components and data flow

1. `ExtrasPlugin.onEnable()` constructs the event service before the domain
   services.
2. Domain service constructors receive an internal event publisher while their
   public types remain the existing service interfaces.
3. A successful domain mutation commits its repository transaction, creates the
   corresponding immutable event, and publishes it after leaving the mutation
   lock.
4. `ExtrasServices` is constructed from the four live domain services and
   registered alongside them.
5. `DefaultSocialService` is constructed from the friend, party, and title
   services and registered as `SocialService`.
6. `ExtrasPlugin.onDisable()` closes the event service, clears subscriptions,
   unregisters all new services, and retains the existing shutdown behavior.

## Error and lifecycle behavior

- `ExtrasServices` never returns null service members while the plugin is
  enabled.
- `SocialService.snapshot` returns empty optional/set values for unknown
  players, matching the existing friend, party, and title read semantics.
- Subscription cancellation is idempotent and safe during shutdown.
- Publishing to zero subscribers is valid.
- A subscriber exception is caught, logged by the Paper-owned implementation,
  and does not roll back committed domain state.
- Events are not emitted for failed results, duplicate grants/requests, or
  successful no-op operations that do not change state.
- No event is published before persistence succeeds; a crash after commit and
  before delivery may lose the in-process event. Durable notifications require
  a later transactional outbox design.

## Testing and verification

Add behavior-focused tests for:

- façade accessors returning the exact live domain service instances;
- immutable `SocialSnapshot` values and empty-state behavior, including
  defensive copying of a mutable input set;
- event creation only after successful mutations;
- no event on validation failures, duplicate operations, or state-preserving
  no-ops;
- one publisher invocation per emitted event, with at most one delivery attempt
  per matching active subscription;
- `deleteAllRead` emits one `MailDeleted` event per actual deleted mail ID,
  emits none for zero-row or retained messages, and still returns the count;
- subscriber isolation when one callback throws;
- idempotent subscription cancellation and shutdown cleanup;
- event and snapshot APIs containing no Paper or repository dependencies;
- plugin lifecycle registration of all three new SPIs while retaining the four
  existing registrations.

Existing domain tests remain authoritative for persistence and mutation
invariants. No full event-log or notification durability tests are required in
this slice because durability is explicitly out of scope.

## Non-goals

- Replacing or merging the four existing domain SPIs.
- A persistent notification inbox or event replay.
- Block, ignore, or privacy policy enforcement.
- Player-facing commands, GUIs, chat, tablist rendering, or join messages.
- A generic key/value event payload mechanism.
- Cross-domain transactions or a global consistency lock.
- New gameplay domains such as economy, teleportation, or item storage.

## Future extensions

After this composition layer proves useful to downstream consumers, a durable
notification service can consume selected typed events through an outbox or
transactional projection. A separate block/privacy SPI can then enforce policy
across friend, party, and mail mutations. Larger independent domains should be
added only with their own focused APIs and persistence boundaries.
