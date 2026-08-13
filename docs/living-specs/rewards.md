# Rewards, Leaderboards, and Login Streaks (modular-extras)

> Status: active
> Last updated: 2026-08-12
> Owners: jlo

## Intent

Give players one persistent daily criterion and reward, automatic gameplay
progress tracking, daily/weekly rankings, and consecutive UTC login streaks.
Criteria are public Bukkit-free API values so downstream plugins can define
and consume the same activity vocabulary. An optional workflowz provider can
propose a validated daily criterion; local YAML criteria remain the offline
source of truth.

## Boundaries

### In scope

- `dev.mintychochip.api.rewards` criterion records, progress values, immutable
  snapshots, result types, daily/leaderboard/streak/provider SPIs
- SQLite persistence in `rewards.db`
- UTC-day criterion rotation through the configured fallback pool
- Paper event mapping for block breaks, entity kills, crafting, XP, and joins
- Idempotent daily reward claims with item/XP/allow-listed command descriptors
- Daily and UTC ISO-week leaderboard totals
- Persistent current/best login streaks with same-day idempotence and gap reset
- Optional bounded workflowz HTTP proposals with deterministic fallback
- `/rewards` command aliases `daily`, `leaderboard`, `streak`

### Out of scope / non-goals

- Cross-server or network-synchronized rankings
- Economy/Vault integration
- Generated command execution or unrestricted config commands
- Monthly/rolling leaderboard windows
- GUI presentation
- Durable workflowz request queues or replay

## Invariants

- API reward and criterion values contain no Bukkit/Paper classes.
- A criterion target and reward amount are positive; material keys are normalized
  namespace/key values.
- Progress with a nonmatching kind/key is ignored, not applied to another
  criterion.
- Progress clamps at the criterion target.
- A successful claim persists before Paper executes its reward descriptor;
  repeated claims return `ALREADY_CLAIMED`.
- Criterion rows are keyed by UTC date; when absent, selection is deterministic
  from the pool using the UTC epoch day.
- Same-day login is idempotent; yesterday increments; any older login resets the
  current streak to one; best streak never decreases.
- Workflowz responses are bounded, structured, validated through API factories,
  and rejected if they contain executable fields.
- Rewards command actions require `extras.rewards.use`; rerolls require
  `extras.rewards.admin`.

## Implementation guidance

- `api` = `Criterion`, concrete criterion records, `Reward`, progress/snapshot
  records, and `DailyRewardService`, `LeaderboardService`,
  `LoginStreakService`, `CriterionProvider`.
- `core` = `SqliteRewardStore`, `DailyWindow`,
  `DefaultDailyRewardService`, `DefaultLeaderboardService`, and
  `DefaultLoginStreakService`; one store owns the SQLite connection.
- `paper` = `RewardsConfig`, `RewardsCommand`, `RewardsListener`, and
  `WorkflowzCriterionProvider`; Paper executes descriptors after core claims.
- `ExtrasPlugin` owns construction, ServicesManager registration, event
  registration, command registration, and shutdown. The reward store is closed
  once by the daily service to avoid double-closing shared SQLite state.

## Current

- [x] API criterion model and immutable reward/progress values
- [x] Daily reward service with persistence, clamping, claim idempotence, and
      deterministic UTC rotation
- [x] Login streak persistence and UTC gap semantics
- [x] Daily/weekly leaderboard projection
- [x] Configured criterion pool and bundled `rewards.yml`
- [x] Paper event listener and `/rewards` command
- [x] Optional workflowz HTTP provider with offline-safe behavior
- [x] Focused and complete JUnit suite passing
- [x] Committed-domain events for successful claims and streak changes

## Next

- [ ] Add a periodic Folia-safe play-time ticker for `PLAY_TIME` criteria
- [ ] Execute streak milestone rewards from configured milestones
- [ ] Display viewer rank and player names in the leaderboard API view

## Future

- [ ] Reward GUI and pagination
- [ ] Cross-server leaderboard storage
- [ ] Durable workflowz proposal cache/outbox

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-12 | Criterion contract lives in `api` | Required by user; allows downstream Bukkit-free consumers |
| 2026-08-12 | UTC date and UTC ISO week windows | Stable server-independent reset semantics |
| 2026-08-12 | YAML pool is deterministic fallback; workflowz optional | Server remains playable without a network service |
| 2026-08-12 | Paper owns reward execution | Core stays platform-neutral and claims remain idempotent |
