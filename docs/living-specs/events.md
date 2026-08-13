# Events (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-12
> Owners: jlo

## Intent

A Bukkit-free event SPI so downstream plugins can subscribe to committed
domain changes in Extras. Events are typed, immutable records carrying an
event UUID and occurrence timestamp plus domain-specific ids; no event
exposes a repository row, Bukkit object, or item content.

## Boundaries

### In scope

- `dev.mintychochip.api.events` subscription surface:
  `ExtrasEventService` (all-events and type-filtered subscribe), idempotent
  `EventSubscription` handles, and the sealed `ExtrasEvent` record set
- In-process synchronous bus (`InProcessExtrasEventService`) with per-call
  delivery snapshots, subscriber failure isolation, and close semantics
- Emission from friend, party, title, mail, chat-preference, trade, and
  rewards/streak domains after successful persistence, outside mutation locks
- Per-row event fan-out for bulk `deleteAllRead`

### Out of scope / non-goals

- No durable subscription, replay, or outbox (in-memory only)
- No exactly-once or at-least-once delivery guarantees across crashes
- No Bukkit-thread promise; subscribers touching Bukkit schedule their own work
- No event for state-preserving no-ops or failed operations
- No forged publication from consumers (bus handles are injected, not exposed)

## Invariants

- Every event has a unique `eventId` and an `occurredAt` from the domain clock.
- Events are published only after the corresponding persistence operation
  succeeded: SQLite-backed domains after the write, titles after the JSON
  write.
- A single-row state change emits exactly one event; `deleteAllRead` emits one
  `MailDeleted` per actually deleted id; zero-row and retained rows emit none.
- Publication happens outside the domain mutation lock.
- Subscriber exceptions are isolated: they never block other subscribers or
  the domain mutation, and are reported to the injected error handler.
- Closing the bus atomically deactivates and clears all subscriptions; new
  subscriptions are rejected and `publish` becomes a no-op.

## Implementation guidance

- `api/events/` owns the sealed `ExtrasEvent` contract and all concrete
  records; `core/InProcessExtrasEventService` is the single bus
  implementation (CopyOnWriteArrayList, no executor or background thread).
- Domain services receive the bus through their constructor; default
  constructors use `InProcessExtrasEventService.noOp()` so existing tests and
  callers keep working without a bus.
- Services take a `java.time.Clock` for event timestamps where they did not
  already have one.
- `SqliteMailRepository` reports changed rows/mail ids (`markRead`/
  `markUnread` return boolean; `deletedIdsAllRead` returns deleted ids inside
  the same transaction) so the service emits only real state changes.
- `ExtrasPlugin` constructs the bus first, injects it into every domain
  service, registers it on the ServicesManager as `ExtrasEventService`, and
  closes it on disable.

## Current

- [x] `ExtrasEventService` / `ExtrasEvent` / `EventSubscription` API contracts
- [x] Thread-safe in-process bus with filter, cancel, close, failure isolation
- [x] Friend events (5 records) + tests
- [x] Party events (8 records, incl. logout auto-transfer) + tests
- [x] Title events (4 records, actual-change-only) + tests
- [x] Mail events (5 records, changed-row/id reporting) + tests
- [x] Chat preference events (3 records) + tests
- [x] Trade lifecycle events (5 records) + tests
- [x] Reward claim / login streak events (2 records) + tests
- [x] `ExtrasPlugin` construction, injection, registration, close
- [x] Green `./gradlew test`

## Next

- [ ] Downstream example subscriber or documentation snippet
- [ ] Event-driven chat formatter (equipped title re-render)

## Future

- [ ] Cross-server event propagation via a durable outbox
- [ ] Generic subscription wildcards besides exact-type matching

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-10 | Typed sealed records instead of generic string payloads | Compile-time safety and stable fields for consumers |
| 2026-08-10 | Publish after commit, outside mutation locks | Subscribers can't block or re-enter repository transactions |
| 2026-08-10 | In-process synchronous bus, no executor | Matches domain scale; no ordering surprises |
| 2026-08-10 | `deleteAllRead` fans out per-row ids | Public count stays, but consumers get exact mail ids |
| 2026-08-12 | Events extended to chat, trade, and rewards domains | Existing lifecycle state machines already had typed transitions |

## Open questions

- [ ] Should any future durable event log replay on subscribe? (currently: no)
