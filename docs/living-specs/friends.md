# Friends (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-08
> Owners: jlo

## Intent

Persistent mutual friendships for the ModularExtras plugin, mirroring the
party domain's shape: Bukkit-free SPI, SQLite persistence, `/friend` command,
and presence announcements to online friends.

## Boundaries

### In scope
- Friend requests: send, accept/decline (recipient), cancel (requester)
- Mutual friendships: remove, list with online/offline presence
- Request/join/quit announcements to online players
- SQLite persistence of requests and friendships across restarts
- `FriendService` SPI registration for downstream consumers

### Out of scope / non-goals
- No request expiry (requests persist until acted upon)
- No friend chat, no friend teleport, no nickname/favorite tiers
- No per-player privacy flags (e.g. hiding online status)
- No leader/roles — friendships are symmetric

## Invariants

- Friendships are mutual: one canonical row per unordered pair
  (`player_a < player_b`); `areFriends(a, b)` == `areFriends(b, a)`.
- Friendship requires accepting a pending request (no silent joins).
- Requests are directional and never expire; either direction blocks a
  duplicate pair while pending.
- Only the recipient accepts/declines; only the requester cancels.
- Accept must be atomic against a concurrent `removeFriend` and a duplicate
  send (single lock in `DefaultFriendService`).
- All read views are immutable snapshots.
- Every repository mutation is one SQLite transaction.

## Implementation guidance

- Friends live beside parties: `api` = Bukkit-free SPI + immutable value
  types (`FriendService`, `FriendResult`, `FriendRequest`, `Friendship`);
  `core` = synchronized `DefaultFriendService` over a single-connection
  `SqliteFriendRepository`; `paper` = `FriendCommand` (BasicCommand) and
  `FriendLifecycleListener`.
- Friends use their own `friends.db` (schema in `SqliteFriendRepository`),
  isolated from `party.db`; reuse `SqliteConnection` with a schema argument.
- Canonical pairing lives in `SqliteConnection.canonicalPair`; the repository
  stores the ordered pair, the service never sees ordering.
- Paper command helpers (`PlayerIds.requirePlayer`, `resolvePlayerId`,
  `playerName`, `isOnline`) are shared with `PartyCommand` — do not duplicate
  them.
- Presence announcements fire from `PlayerJoinEvent`/`PlayerQuitEvent` only,
  matching the party listener convention.
- Do not put presence hooks or friend-count getters into the SPI.

## Current

- [x] `FriendService` SPI (send/accept/decline/cancel/remove/areFriends/read views)
- [x] SQLite repository (`friend_requests`, `friendships`) with transactions
- [x] `DefaultFriendService` with mutual/canonical invariants
- [x] `/friend` command (request/add, accept/decline/deny, cancel, remove, list, requests) + presence listener
- [x] Committed-domain events for request/accept/decline/cancel/removal
- [x] Green build: 48 tests, shaded jar

## Next

- [ ] Configurable friend cap or per-player privacy (hide online status)

## Future

- [ ] Friend chat channel
- [ ] Request expiry or auto-decline after N days

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-08 | Friendships are mutual with directional, non-expiring requests | Standard Minecraft friend model; matches party invite flow |
| 2026-08-08 | Separate `friends.db` from `party.db` | Keeps connection isolation; schema independent |
| 2026-08-08 | Canonical pair stored as `player_a < player_b` | One row per unordered pair; simple uniqueness |
| 2026-08-08 | Presence announcements in a paper listener, not the SPI | Mirrors party listener convention; SPI stays implementation-neutral |

## Open questions

- [ ] Should friends be visible server-wide (e.g. `list all`) or kept per-player?
