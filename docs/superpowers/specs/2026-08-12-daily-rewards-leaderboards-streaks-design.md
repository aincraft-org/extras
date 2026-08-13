# Daily Rewards, Leaderboards, and Login Streaks — Design

**Date:** 2026-08-12
**Status:** Draft (pending user review)
**Owner:** jlo
**Workspace:** ModularExtras (`/home/jlo/dev/extras`)

## Goal

Add a daily-rewards, leaderboard, and login-streak subsystem to the Extras
Paper plugin. Each UTC day, the server picks an **arbitrary criterion** (e.g.
"mine 64 diamond ore", "kill 30 zombies", "craft 16 iron ingots", "earn 2000
XP", "log in 7 days straight", "play 2 hours"). Players earn progress toward
it through normal gameplay; whoever completes it first claims that day's
reward. When no one claims it, the criterion rotates automatically at the
next daily reset — or on admin demand — through a configured pool, or through
an optional **workflowz** content service that proposes the next criterion.
Login streaks are tracked independently: consecutive daily logins, with
milestone rewards and streak-break reset.

The criterion system lives in the **API layer** (`dev.mintychochip.api`,
Bukkit-free) as a typed contract, per the user's steer: "you develop a
criteria in the apis please".

## Context

ModularExtras exposes several Bukkit-free domain SPIs (`FriendService`,
`PartyService`, `TitleService`, `MailService`, `ChatService`), each with a
`core` implementation over SQLite and a `paper` command/listener adapter.
`ExtrasPlugin` owns construction/registration/shutdown on the ServicesManager.
This subsystem follows the same shape: `api` SPI + immutable values, `core`
implementations with a single mutation lock each over SQLite (`rewards.db`),
`paper` commands/listeners/config.

**workflowz** is the user's LLM-backed quest-content provider living in a
separate repo (`/home/jlo/dev/quests`): a boundary port
`QuestContentProvider.propose(request)` returning a validated `QuestProposal`,
with `DISABLED`/`OPTIONAL`/`REQUIRED` modes and a deterministic fallback. This
design reuses that *pattern* — an optional HTTP JSON proposal service with
validation and deterministic fallback — without a hard dependency on the
uncommitted quests repo. Extras embeds a small Java-standard HTTP client; the
workflowz wire contract is defined in this spec so a future QuestMark
integration can point at the same endpoint format.

## Design decisions

### 1. API-layer criterion contract

All criterion types, parameters, validation, and result values live under
`dev.mintychochip.api.rewards`, with **no Paper/Bukkit imports** (standard
library + the plugin's own API values only). This is the user's explicit
requirement.

```java
// Criterion.java
public sealed interface Criterion {
    String id();                       // stable id, e.g. "2026-08-12::mine-diamond"
    String description();              // player-facing, e.g. "Mine 64 Diamond Ore"
    int target();                      // progress goal > 0
    Reward reward();                   // what claiming pays

    /** Builder-style factory that validates and normalizes. */
    static Criterion of(String type, Map<String, String> params) { … }

    record MineBlocks(String id, String description, MaterialKey block, int target, Reward reward) implements Criterion {}
    record KillEntities(String id, String description, MaterialKey entity, int target, Reward reward) implements Criterion {}
    record CraftItems(String id, String description, MaterialKey item, int target, Reward reward) implements Criterion {}
    record GainXp(String id, String description, int target, Reward reward) implements Criterion {}
    record LoginDays(String id, String description, int target, Reward reward) implements Criterion {}
    record PlayTime(String id, String description, Duration target, Reward reward) implements Criterion {}
}
```

- `MaterialKey` is a Bukkit-free `record MaterialKey(String namespace, String key)`
  (e.g. `minecraft:diamond_ore`), validated against `[a-z0-9_.-]+(:[a-z0-9_./-]+)?`.
- `reward` is `enum RewardType { ITEM, XP, COMMAND }` + serialized payload
  (item material + count, XP amount, or command string). Commands are
  allow-listed server-side in config; the API only carries the descriptor.
- `Criterion.of` validates: known type, required params, `target > 0`,
  non-blank id/description, valid material keys, non-negative reward amounts.
  It throws `IllegalArgumentException` with a field path on failure (matching
  the QuestValidator diagnostic style) and normalizes trims.
- The sealed interface + records are the "arbitrary criterion" surface:
  any future criterion type is a new record + a progress sink method.

### 2. Three SPIs, each Bukkit-free

```java
public interface DailyRewardService {
    CriterionSnapshot activeCriterion();                        // today's (or forced) criterion
    DailyRewardStatus status(UUID playerId);                    // progress, target, claimed
    DailyRewardResult recordProgress(UUID playerId, CriterionProgress p);
    DailyRewardResult claim(UUID playerId);
    RewardResult forceCriterion(Criterion c);                   // admin reroll
    RewardResult rotate();                                      // advance to next pool criterion (daily reset)
}

public interface LeaderboardService {
    LeaderboardView leaderboard(LeaderboardPeriod period);      // DAILY | WEEKLY
    void recordTotal(UUID playerId, CriterionKind kind, int amount); // internal-ish, called by DailyRewardService
}

public interface LoginStreakService {
    StreakSnapshot streak(UUID playerId);
    StreakResult recordLogin(UUID playerId);                    // today's first join: +1 or reset
    StreakResult claimMilestone(UUID playerId);                 // optional: claim current milestone reward
}
```

Immutable value records: `CriterionSnapshot`, `DailyRewardStatus`,
`CriterionProgress`, `CriterionKind`, `LeaderboardPeriod`, `LeaderboardView`
(list of `LeaderboardEntry`), `StreakSnapshot`, and result enums
`DailyRewardResult`, `RewardResult`, `StreakResult` (`OK`, `ALREADY_CLAIMED`,
`NOT_CLAIMABLE`, `INVALID_CRITERION`, `UNKNOWN_PLAYER`, `NO_STREAK`, …).

`CriterionProgress` is the paper→core incidence: `record CriterionProgress(CriterionKind kind, String key, int amount)`
(e.g. `kind=MINE_BLOCKS, key="minecraft:diamond_ore", amount=1`). The service
filters by the active criterion: if the kind/key doesn't match, it ignores
the progress (no-op, no error). This keeps the paper event layer dumb and the
API authoritative about what counts.

### 3. Core implementations over SQLite

Follow the `SqliteConnection` + repository pattern exactly:

- `SqliteConnection` is reused with a **new schema** (see §5). It currently
  owns one connection per store; rewards gets its own `rewards.db` via
  `new SqliteConnection("jdbc:sqlite:.../rewards.db", REWARDS_SCHEMA)`.
- Tables: `daily_criteria` (id, day, kind, key, target, description,
  reward_json, forced), `daily_progress` (player, day, criterion_id, amount,
  claimed), `streaks` (player, current_streak, best_streak, last_login_date),
  `leaderboard_totals` (player, period, kind, key, total, updated_at),
  `milestone_claims` (player, milestone_ordinal, claimed_date).
- `DefaultDailyRewardService`, `DefaultLeaderboardService`,
  `DefaultLoginStreakService` each follow the single-mutation-lock pattern;
  reads are lock-free; every mutation is one SQLite transaction.
- The services share a `DailyWindow` helper (injected `Clock`) that computes
  the UTC date key (`yyyy-MM-dd`), the week key (UTC ISO week), and
  detects date rollover. Leaderboard period queries use these keys.

**Streak semantics (explicit):**
- `recordLogin` on a UTC date that differs from the stored `last_login_date`:
  if `last_login_date == yesterday(utc)` → streak+1; if older → **reset to 1**
  (streak broken); if same date → no change (idempotent second join).
- `best_streak` is monotonic non-decreasing.
- Milestones: config-defined `streakMilestones` map ordinal→Reward;
  `claimMilestone` pays once per milestone; `ALREADY_CLAIMED` on repeat.

**Daily reward semantics:**
- `activeCriterion()`: if today's `daily_criteria` row exists, return it;
  else pick `forced`/pool → insert row → return. Rotation happens lazily on
  first read after a new UTC day (no scheduler needed in core; the paper
  listener also triggers `rotate()` on join if date changed).
- `recordProgress` matches the active criterion's kind+key; clamps at target
  (no overflow); persists progress; updates `leaderboard_totals` for the
  period only when kind matches (all progress counts toward leaderboards,
  regardless of the day's active criterion — leaderboards are arbitrary too).
- `claim`: only when progress >= target AND not already claimed that day;
  sets `claimed`; returns the reward descriptor. Reward *execution* is paper's
  job (item give / XP / allow-listed command), never core's.

### 4. workflowz criterion provider (optional, offline-safe)

Follow the QuestMark provider pattern:

- `core/provider`-style port in the API or a small `api.rewards.provider`
  subpackage? **Decision: keep the port in `api.rewards`** (`CriterionProvider`),
  with the HTTP implementation in `paper` (it's a paper-side integration
  concern, like QuestMark's `WorkflowzQuestService`).

```java
// api: the port
public interface CriterionProvider {
    Optional<Criterion> proposeActiveCriterion();   // may be empty on failure
}
```

- `paper/WorkflowzCriterionProvider` implements `CriterionProvider`:
  - `HttpClient` (Java standard, no new dependency), bounded timeouts
    (connect 3s, request 8s), response size cap (64 KiB), strictly structured
    JSON decode (regex/JSON-token scan like QuestMark's decoder; reject any
    `command`/`run_command`/`actions` fields), then `Criterion.of(...)`
    validation. Invalid/oversized/any failure → `Optional.empty()`.
  - Mode `DISABLED`/`OPTIONAL` (`Optional.empty()` → fallback pool)/
    `REQUIRED` (failure → day uses pool + logs WARN).
  - The HTTP request carries: schemaVersion, UTC date key, pool summary,
    recent winners, last N criteria summaries — bounded, no raw chat, no
    commands. The response carries: `type`, `key`, `target`, `description`,
    `reward` (type+payload), `id`.
  - **Never grants rewards, mutates state, or executes commands.** Paper
    validates before any use, exactly like QuestMark.
  - `providerMode` + `endpoint` config keys; default `OPTIONAL`/empty
    endpoint → pool-only.

### 5. Pool, config, and rewards.yml

New `rewards.yml` in the plugin data folder (checked-in default under
`src/main/resources/`), parsed by `RewardsConfig` (uses Bukkit's
`YamlConfiguration` / SnakeYAML already on the classpath via Paper):

```yaml
daily-reset-hour: 0            # UTC hour; 0 = midnight (only 0 in v1)
provider:
  mode: OPTIONAL               # DISABLED | OPTIONAL | REQUIRED
  endpoint: ""                 # empty => pool-only
criterion-pool:                # ordered fallback/proposal pool
  - { type: MINE_BLOCKS,   block: minecraft:diamond_ore,      target: 64,  description: "Mine 64 Diamond Ore" }
  - { type: KILL_ENTITIES, entity: minecraft:zombie,          target: 30,  description: "Kill 30 Zombies" }
  - { type: CRAFT_ITEMS,   item: minecraft:iron_ingot,        target: 16,  description: "Craft 16 Iron Ingots" }
  - { type: GAIN_XP,       target: 2000,                      description: "Earn 2000 XP" }
  - { type: LOGIN_DAYS,    target: 7,                         description: "Log in 7 days straight" }
  - { type: PLAY_TIME,     target-minutes: 120,               description: "Play for 2 hours" }
daily-reward:
  type: ITEM
  item: minecraft:emerald
  count: 16
streak-milestones:           # ordinal -> reward
  3:  { type: ITEM, item: minecraft:golden_apple,  count: 1 }
  7:  { type: XP,   amount: 500 }
  30: { type: COMMAND, command: "give %player% minecraft:diamond 8" }
command-allowlist: ["give"]
```

- `Criterion.of` maps the YAML entries to validated `Criterion` records;
  invalid pool entries are skipped at load with a WARN (never a crash), and
  if the pool is empty the day uses a built-in default criterion
  (`LOGIN_DAYS target 1`) so the subsystem is always functional.
- The `daily-reward` is the generated `reward` for pool/proposal criteria
  that don't specify their own; proposal criteria may override just the
  criterion, keeping the fixed daily payout — this is the "fixed daily
  reward" interpretation (reward is steady, criterion rotates).

### 6. Paper adapters

- `RewardsCommand` (BasicCommand; aliases `daily`, `lb`, `streak`):
  - `/rewards` — status: active criterion, my progress, claimed?
  - `/rewards claim` — claim when target met.
  - `/rewards top [daily|weekly]` — leaderboard (top 10 + my rank; ties by
    earlier `updated_at`).
  - `/streak` — current/best streak, next milestone.
  - Admin (`extras.rewards.admin`): `/rewards reroll` (force next criterion
    now: workflowz if up, else pool), `/rewards set <player> progress <n>`,
    `/rewards set <player> streak <n>`.
- `RewardsListener` (implements `org.bukkit.event.Listener`):
  - `PlayerJoinEvent` → `loginStreakService.recordLogin` (async? no — cheap
    SQLite write on main thread is the existing convention: FriendLifecycle
    does sync service calls; keep it sync and simple), fire rotate-check
    (if UTC date changed since last seen, `rotate()` then announce new
    criterion), and send the player their streak message.
  - `BlockBreakEvent` → map block type → `MINE_BLOCKS` progress at
    `EventPriority.MONITOR` (don't block legit gameplay; progress on success
    only — `!isCancelled`).
  - `EntityDeathEvent` → killer is player → `KILL_ENTITIES`.
  - `CraftItemEvent` / `PrepareItemCraftEvent` → `CRAFT_ITEMS` (on craft
    completion, count output).
  - `PlayerExpChangeEvent` → `GAIN_XP`.
  - `PlayerJoinEvent` + scheduler tick (every 60s) → `PLAY_TIME`
    (track join time, add elapsed on quit/join; v1: accumulate online seconds
    into progress per UTC day).
- Rewards are **executed paper-side**: `RewardsListener`/command claims the
  reward then grants items/XP/allow-listed commands in hand/economy, matching
  `MailService` attachment-claim style (claim returns descriptor, paper
  performs).
- Registering: `ExtrasPlugin.onEnable` constructs repos/services, registers
  the three SPIs on ServicesManager at Normal priority, registers
  `RewardsCommand` via `LifecycleEvents.COMMANDS`, registers `RewardsListener`
  via `Bukkit.getPluginManager().registerEvents`, and closes repos in
  `onDisable` (mirroring party/friend/chat lifecycle).

### 7. Error handling and lifecycle

- Criterion decode/validation failure from config or workflowz → WARN, skip
  entry / fall back to pool / built-in default; never crash the plugin.
- `DailyRewardService` operates even with no persisted criteria (lazy
  creation on first read).
- `recordProgress` with mismatched kind/key → no-op OK (by design).
- `claim` when progress < target → `NOT_CLAIMABLE`; when already claimed →
  `ALREADY_CLAIMED`; unknown player → `UNKNOWN_PLAYER`.
- `forceCriterion` persists a new row with `forced=1` for the current UTC
  day, replacing the active one; rotation after a new day always prefers a
  real rotation over reusing a forced criterion.
- Services are closed in `onDisable` (repository `close()`), all GUIs use
  existing close patterns, no new threads beyond the existing scheduler
  conventions; no async listeners (matches FriendLifecycle sync convention).

### 8. Testing

Behavior-first, matching existing test structure (`DefaultFriendServiceTest`
style, `@TempDir` SQLite repos):

- `CriterionTest`: `of()` valid/invalid, target>0, material key validation,
  trimming, each record's accessors.
- `DefaultDailyRewardServiceTest`: lazy criterion creation on first read,
  rotation at date rollover, pool fallback, forced criterion, progress
  kind/key filtering, clamp at target, claim-once, `ALREADY_CLAIMED`,
  `NOT_CLAIMABLE`, leaderboard total updates, UTC day boundary (Clock-injected
  midnight), empty-pool → built-in default.
- `DefaultLoginStreakServiceTest`: +1 same-day idempotent, +1 consecutive
  day, reset on gap, best-streak monotonic, milestone claim once /
  `ALREADY_CLAIMED`, UTC boundary around midnight.
- `DefaultLeaderboardServiceTest`: period filter (daily vs weekly), ranking
  order, tie-break, unknown-player empty, immutability of returned views.
- `WorkflowzCriterionProviderTest`: OK decode → valid criterion; malformed →
  empty; oversized → empty; `command` field → empty (rejected); timeout →
  empty (fake provider); OPTIONAL vs REQUIRED mode behavior; response capped.
- `RewardsConfigTest`: parses valid YAML, skips invalid pool entries with
  WARN, empty pool → default, command allowlist validation.
- `PluginDescriptorTest`: assert new commands/permissions declared in
  `paper-plugin.yml` (extend existing structural test).
- Convention: every test asserts observable behavior (results, state,
  persistence across close/reopen), not implementation details.

## Non-goals

- No persisted "event log" or outbox; in-memory subscription only (matches
  existing domains).
- No cross-server leaderboards.
- No arbitrary command execution from workflowz or config beyond the
  allowlist.
- No economy integration (no Vault); rewards are item/XP/allow-listed command.
- No GUI menus in v1 (command-driven only); GUIs can follow later.
- No leaderboard "window" beyond UTC daily and UTC ISO weekly (no monthly).
- No per-player privacy/hiding on leaderboards.
- `daily-reset-hour` is fixed at 0 (UTC midnight) in v1.

## Decision log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-12 | Criterion contract lives in `api` (`dev.mintychochip.api.rewards`) | User's explicit steer: "develop a criteria in the apis" |
| 2026-08-12 | workflowz is an OPTIONAL proposal provider with deterministic pool fallback | Offline-safe, matches QuestMark's `OPTIONAL` mode; rewards authority stays in Paper |
| 2026-08-12 | UTC midnight daily reset, UTC ISO week | Consistent with QuestMark's UTC windows |
| 2026-08-12 | Reward is fixed `daily-reward`; criterion rotates | "Daily rewards" = steady payout, arbitrary criterion rotates; workflowz may override criterion only |
| 2026-08-12 | `recordProgress` ignores mismatched kind/key (no-op) | Paper event layer stays dumb; API is authoritative |
| 2026-08-12 | Streak breaks on any ≥2-day gap; same-day login idempotent | Standard streak semantics, testable around UTC boundary |
| 2026-08-12 | Commands executed by Paper only, allow-listed | Workflowz/config never execute commands; security boundary |

## Open questions

- Should `/rewards reroll` be permission-gated to `extras.rewards.admin`
  (default yes) — confirm.
- Should leaderboards reset weekly totals at UTC Monday or keep rolling 7
  days? (Chosen: UTC ISO week reset.)
