# Parties (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-08
> Owners: jlo

## Intent

Standalone Paper plugin that owns persistent player parties. Inviting people
to parties, accepting/declining, leader transfer, presence announcements, and
SQLite persistence — exposed as a `PartyService` SPI on the Bukkit
ServicesManager so other plugins can integrate (spawn-to-party, party combat,
grouping).

## Boundaries

### In scope
- Party creation, invite→accept/decline, leave, kick, disband, leader transfer
- Presence (login/out) announcements to online party members
- SQLite persistence of parties/members/invites across restarts
- `PartyService` SPI registration for downstream consumers

### Out of scope / non-goals
- No party chat, no spawn/teleport-to-party, no invite GUI, no quests
- Presence hooks are the paper listener's job, not part of the SPI
- No configurable size limit at runtime (constructor-injected, default 4)

## Invariants

- A party has exactly one leader, who is also a member.
- Membership requires accepting a pending invitation (no silent joins).
- Invites are per-party and expire (default 60s).
- Leader leaving auto-transfers to the longest-standing member; an empty
  party is deleted.
- Cap (default 4) is enforced atomically against concurrent accepts.
- All read views are immutable snapshots.
- Every repository mutation is one SQLite transaction.

## Implementation guidance

- `api` = Bukkit-free SPI + immutable value types.
- `core` = synchronized `DefaultPartyService` over a single-connection
  `SqlitePartyRepository`. Mutations guard check-then-act invariants with a
  single internal lock; reads hit a member-keyed cache first.
- `paper` = `ExtrasPlugin` (ServicesManager registration, lifecycle command
  registration, close on disable), `PartyCommand` (BasicCommand), and
  `PartyLifecycleListener` (announce from `PlayerJoinEvent`/`PlayerQuitEvent`
  only — never from async pre-login).
- Do not put presence hooks or size-limit getters into the SPI.
- SQLite access is serialized on one connection (single-writer).

## Current

- [x] `PartyService` SPI (create/invite/accept/decline/leave/kick/disband/transfer/read)
- [x] SQLite repository with transactions, cascade delete, expiry filtering
- [x] `DefaultPartyService` with cap/leadership/invite invariants
- [x] `/party` BasicCommand + presence listener + ServicesManager registration
- [x] Committed-domain events for create/invite/accept/decline/leave/kick/disband/transfer
- [x] Green build: 26 tests, shaded jar

## Next

- [ ] Configurable party size limit (`config.yml`)
- [ ] `/party list` for non-members / server-wide party view

## Future

- [ ] Party chat channel
- [ ] Teleport-to-party / spawn grouping
- [ ] Invite GUI (click-to-accept)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-08 | Persistence uses SQLite (raw JDBC, single connection) | Per user; deterministic and matches sibling modular plugins |
| 2026-08-08 | Presence hooks live in the paper listener, not the SPI | First-class SPI stays implementation-neutral |
| 2026-08-08 | Mutations synchronized on one internal lock | Cap/leadership check-then-act must be atomic under concurrency |

## Open questions

- [ ] Should invites survive a server restart (currently they do, via SQLite)?
