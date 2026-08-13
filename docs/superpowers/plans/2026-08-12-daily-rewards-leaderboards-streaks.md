# Daily Rewards, Leaderboards, and Login Streaks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Bukkit-free API criterion system, persistent daily rewards, automatic progress tracking, daily/weekly leaderboards, login streaks, and optional workflowz criterion proposals to Extras.

**Architecture:** `dev.mintychochip.api.rewards` owns immutable criterion/activity/result values and three service SPIs. `core` implements persistence and state transitions using one SQLite `rewards.db`, injected UTC clocks, and transaction-safe repositories. `paper` loads YAML configuration, maps Paper events to API progress, exposes commands, executes validated rewards, and optionally calls workflowz off-thread; workflowz failures always fall back to deterministic local criteria.

**Tech Stack:** Java 21, Paper 1.21.11 API, SQLite JDBC, JUnit 5, Java `HttpClient`, Bukkit `YamlConfiguration`. No new runtime dependencies.

## Global Constraints

- Criterion definitions and public services MUST be Bukkit-free and live under `dev.mintychochip.api.rewards`.
- Paper remains authoritative for event mapping and reward execution; workflowz only proposes validated criterion content.
- Daily and weekly windows MUST use UTC date and UTC ISO week keys.
- Login streak updates MUST be idempotent for repeated same-day joins and reset after a missed UTC day.
- Progress MUST be filtered by criterion kind/key and clamped at the target.
- Claims MUST be persisted before Paper executes the returned reward descriptor, preventing duplicate payouts.
- Workflowz HTTP calls MUST be bounded, asynchronous, size-limited, reject executable fields, and have deterministic fallback.
- Commands and config reward commands MUST be permission/allow-list controlled; generated content MUST NOT execute commands.
- Existing uncommitted chat-channel changes MUST remain intact; do not reformat or revert unrelated files.
- Skip formatters, linters, and project-wide suites during individual tasks; run the complete verification once at the end.

---

### Task 1: Add API criterion and reward value types

**Files:**
- Create: `src/main/java/dev/mintychochip/api/rewards/Criterion.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/CriterionKind.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/MaterialKey.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/Reward.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/RewardType.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/CriterionProgress.java`
- Test: `src/test/java/dev/mintychochip/api/rewards/CriterionTest.java`

**Interfaces:**
- `Criterion` is a sealed interface with immutable implementations for `MINE_BLOCKS`, `KILL_ENTITIES`, `CRAFT_ITEMS`, `GAIN_XP`, `LOGIN_DAYS`, and `PLAY_TIME`.
- `Criterion.of(...)` validates normalized ids/descriptions/targets/keys and returns an immutable definition.
- `CriterionProgress` is the Paper-to-core event value: kind, key, amount.

- [ ] **Step 1: Write failing value/validation tests**

```java
@Test
void acceptsAndNormalizesAValidBlockCriterion() {
  Criterion criterion = Criterion.mineBlocks(
      "ore-day", " Mine diamond ", new MaterialKey("minecraft", "diamond_ore"), 64,
      Reward.xp(100));
  assertEquals("Mine diamond", criterion.description());
  assertEquals(CriterionKind.MINE_BLOCKS, criterion.kind());
}

@Test
void rejectsBlankIdsInvalidKeysAndNonPositiveTargets() {
  assertThrows(IllegalArgumentException.class, () -> Criterion.gainXp("", "xp", 1, Reward.xp(1)));
  assertThrows(IllegalArgumentException.class, () -> new MaterialKey("Minecraft", "stone"));
  assertThrows(IllegalArgumentException.class, () -> Criterion.gainXp("xp", "xp", 0, Reward.xp(1)));
}

@Test
void criterionProgressCopiesValuesAndRejectsInvalidAmounts() {
  assertThrows(IllegalArgumentException.class,
      () -> new CriterionProgress(CriterionKind.GAIN_XP, "", 1));
  assertThrows(IllegalArgumentException.class,
      () -> new CriterionProgress(CriterionKind.GAIN_XP, "xp", 0));
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew test --tests dev.mintychochip.api.rewards.CriterionTest`
Expected: FAIL because the API types do not exist.

- [ ] **Step 3: Implement immutable API values**

Use Java records for `MaterialKey`, `Reward`, `CriterionProgress`, and each criterion implementation. Use a sealed `Criterion` interface with `kind()`, `id()`, `description()`, `target()`, and `reward()` accessors. Keep material/entity/item identifiers as namespace/key strings; never import Bukkit. Enforce positive reward amounts, nonblank command payloads, and the namespace/key regex in constructors. `PLAY_TIME` stores target seconds; Paper converts elapsed time into progress units.

- [ ] **Step 4: Run the focused test**

Run: `./gradlew test --tests dev.mintychochip.api.rewards.CriterionTest`
Expected: PASS.

- [ ] **Step 5: Commit the API value slice**

```bash
git add src/main/java/dev/mintychochip/api/rewards src/test/java/dev/mintychochip/api/rewards/CriterionTest.java
git commit -m "feat: add Bukkit-free reward criteria API"
```

### Task 2: Add reward, leaderboard, and streak service SPIs

**Files:**
- Create: `src/main/java/dev/mintychochip/api/rewards/DailyRewardService.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/DailyRewardStatus.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/CriterionSnapshot.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/DailyRewardResult.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/LeaderboardService.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/LeaderboardPeriod.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/LeaderboardEntry.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/LeaderboardView.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/LoginStreakService.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/StreakSnapshot.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/StreakResult.java`
- Create: `src/main/java/dev/mintychochip/api/rewards/CriterionProvider.java`

**Interfaces:**

```java
public interface DailyRewardService extends AutoCloseable {
  CriterionSnapshot activeCriterion();
  DailyRewardStatus status(UUID playerId);
  DailyRewardResult recordProgress(UUID playerId, CriterionProgress progress);
  DailyRewardResult claim(UUID playerId);
  DailyRewardResult forceCriterion(Criterion criterion);
  DailyRewardResult rotate(List<Criterion> fallbackPool);
  @Override void close();
}

public interface LeaderboardService {
  LeaderboardView leaderboard(LeaderboardPeriod period, int limit);
}

public interface LoginStreakService extends AutoCloseable {
  StreakSnapshot streak(UUID playerId);
  StreakResult recordLogin(UUID playerId);
  @Override void close();
}

public interface CriterionProvider {
  Optional<Criterion> propose(CriterionProposalRequest request);
}
```

- [ ] **Step 1: Write failing immutable-view tests**

```java
@Test
void leaderboardViewOwnsAnImmutableEntryList() {
  List<LeaderboardEntry> entries = new ArrayList<>();
  LeaderboardView view = new LeaderboardView(LeaderboardPeriod.DAILY, "2026-08-12", entries, 0);
  entries.add(new LeaderboardEntry(UUID.randomUUID(), 1, 3));
  assertTrue(view.entries().isEmpty());
  assertThrows(UnsupportedOperationException.class,
      () -> view.entries().add(new LeaderboardEntry(UUID.randomUUID(), 1, 1)));
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew test --tests dev.mintychochip.api.rewards.RewardApiValueTest`
Expected: FAIL because the SPI/value types do not exist.

- [ ] **Step 3: Implement the SPIs and immutable records**

Use `List.copyOf`, `Objects.requireNonNull`, and explicit result enums. `DailyRewardStatus` contains the current `CriterionSnapshot`, player progress, target, and claimed flag. `LeaderboardView` contains period/window key/criterion description/entries/viewer rank. `StreakSnapshot` contains current, best, last-login `LocalDate`, and next milestone ordinal. `CriterionProposalRequest` contains schema version, UTC date key, fallback summaries, and bounded text only. Keep the provider port synchronous; Paper wraps HTTP calls in `CompletableFuture` off-thread.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.api.rewards.RewardApiValueTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/api/rewards
 git add src/test/java/dev/mintychochip/api/rewards/RewardApiValueTest.java
git commit -m "feat: expose reward, leaderboard, and streak SPIs"
```

### Task 3: Add SQLite reward repositories and UTC window helper

**Files:**
- Create: `src/main/java/dev/mintychochip/core/RewardRepository.java`
- Create: `src/main/java/dev/mintychochip/core/StreakRepository.java`
- Create: `src/main/java/dev/mintychochip/core/LeaderboardRepository.java`
- Create: `src/main/java/dev/mintychochip/core/SqliteRewardStore.java`
- Create: `src/main/java/dev/mintychochip/core/DailyWindow.java`
- Test: `src/test/java/dev/mintychochip/core/SqliteRewardStoreTest.java`
- Test: `src/test/java/dev/mintychochip/core/DailyWindowTest.java`

**Interfaces:**
- `SqliteRewardStore(Path)` owns one connection with the full rewards schema and exposes package-private `connection()`; `close()` is idempotent.
- Repositories receive the shared store and perform one transaction per mutation.
- `DailyWindow` receives an injected `Clock`/`Instant` and returns UTC date key, ISO week key, and yesterday.

- [ ] **Step 1: Write failing persistence/window tests**

```java
@Test
void progressAndStreakRowsSurviveCloseAndReopen() {
  Path file = tempDir.resolve("rewards.db");
  SqliteRewardStore first = new SqliteRewardStore(file);
  UUID player = UUID.randomUUID();
  first.upsertCriterion("2026-08-12", criterion);
  first.setProgress(player, "2026-08-12", criterion.id(), 7, false);
  first.setStreak(player, 3, 4, LocalDate.parse("2026-08-12"));
  first.close();
  SqliteRewardStore second = new SqliteRewardStore(file);
  assertEquals(7, second.progress(player, "2026-08-12", criterion.id()).amount());
  assertEquals(3, second.streak(player).current());
  second.close();
}

@Test
void utcWindowUsesIsoWeekAndMidnightBoundary() { … }
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `./gradlew test --tests dev.mintychochip.core.SqliteRewardStoreTest --tests dev.mintychochip.core.DailyWindowTest`
Expected: FAIL because the store/helper do not exist.

- [ ] **Step 3: Implement schema and repository ports**

Create tables `daily_criteria`, `daily_progress`, `streaks`, `leaderboard_totals`, and `milestone_claims`. Store UUIDs as 16-byte BLOBs and timestamps/dates as text or epoch values consistent with existing repos. Serialize criterion/reward fields into explicit columns (id/day/kind/key/target/description/reward type/payload), not opaque unvalidated blobs. Use `BEGIN`/commit/rollback helpers and `PreparedStatement`s; wrap SQL failures in `IllegalStateException`. Add rank query ordering by total descending, updated timestamp ascending, UUID ascending for deterministic ties.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.core.SqliteRewardStoreTest --tests dev.mintychochip.core.DailyWindowTest`
Expected: PASS, including reopen behavior and UTC ISO week rollover.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/core/RewardRepository.java src/main/java/dev/mintychochip/core/StreakRepository.java src/main/java/dev/mintychochip/core/LeaderboardRepository.java src/main/java/dev/mintychochip/core/SqliteRewardStore.java src/main/java/dev/mintychochip/core/DailyWindow.java src/test/java/dev/mintychochip/core/SqliteRewardStoreTest.java src/test/java/dev/mintychochip/core/DailyWindowTest.java
git commit -m "feat: persist reward state and UTC windows"
```

### Task 4: Implement login streak service

**Files:**
- Create: `src/main/java/dev/mintychochip/core/DefaultLoginStreakService.java`
- Test: `src/test/java/dev/mintychochip/core/DefaultLoginStreakServiceTest.java`

**Interfaces:**
- Constructor: `DefaultLoginStreakService(StreakRepository, Clock)`.
- Implements `LoginStreakService`; every mutation is guarded by one lock.

- [ ] **Step 1: Write failing streak tests**

```java
@Test
void repeatedSameDayLoginIsIdempotent() {
  assertEquals(StreakResult.STARTED, service.recordLogin(player));
  assertEquals(StreakResult.ALREADY_RECORDED, service.recordLogin(player));
  assertEquals(1, service.streak(player).currentStreak());
}

@Test
void consecutiveDayIncrementsAndGapResetsWhileBestPersists() { … }
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultLoginStreakServiceTest`
Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement UTC streak transitions**

Read the stored row inside the mutation lock. Same UTC date returns `ALREADY_RECORDED`; previous date increments; any older date resets to one. Update best with `Math.max`, persist atomically, and return the new snapshot/result. Return empty zero-state snapshots for unknown players. No Bukkit imports.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultLoginStreakServiceTest`
Expected: PASS across midnight, consecutive day, missed day, best streak, and restart tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/core/DefaultLoginStreakService.java src/test/java/dev/mintychochip/core/DefaultLoginStreakServiceTest.java
git commit -m "feat: track persistent login streaks"
```

### Task 5: Implement daily criterion progress and claims

**Files:**
- Create: `src/main/java/dev/mintychochip/core/DefaultDailyRewardService.java`
- Test: `src/test/java/dev/mintychochip/core/DefaultDailyRewardServiceTest.java`

**Interfaces:**
- Constructor: `DefaultDailyRewardService(RewardRepository, Clock, Criterion defaultCriterion)`.
- `setCriterionPool(List<Criterion>)` is a package/API-independent construction/config method; service owns only the active criterion and progress.

- [ ] **Step 1: Write failing reward-service tests**

```java
@Test
void matchingProgressClampsAtTargetAndClaimIsIdempotent() {
  assertEquals(DailyRewardResult.PROGRESSED,
      service.recordProgress(player, new CriterionProgress(CriterionKind.MINE_BLOCKS, "minecraft:diamond_ore", 9)));
  assertEquals(10, service.status(player).progress());
  assertEquals(DailyRewardResult.NOT_CLAIMABLE,
      service.claim(UUID.randomUUID()));
  assertEquals(DailyRewardResult.CLAIMED, service.claim(player));
  assertEquals(DailyRewardResult.ALREADY_CLAIMED, service.claim(player));
}

@Test
void mismatchedKeyDoesNotChangeProgressAndNewUtcDayCreatesNewState() { … }
```

- [ ] **Step 2: Run tests and observe failure**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultDailyRewardServiceTest`
Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement state transitions**

Use one mutation lock. Resolve/create the criterion row for `DailyWindow.dateKey(now)`. Match criterion kind and key; `GAIN_XP`/`LOGIN_DAYS` use their fixed keys, while material criteria compare exact namespace/key. Clamp with `Math.min(target, old + amount)`. Persist progress and return `PROGRESSED`/`COMPLETED` without changing the claimed bit. `claim` requires target reached, atomically flips claimed, and returns the criterion's immutable `Reward`; duplicate claims return `ALREADY_CLAIMED`. `forceCriterion` replaces today's criterion only when no claim exists; `rotate` creates the next-day/default row lazily. Publish no events and execute no rewards in core.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultDailyRewardServiceTest`
Expected: PASS for matching, mismatch, clamp, claim-once, forced criterion, rollover, and restart behavior.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/core/DefaultDailyRewardService.java src/test/java/dev/mintychochip/core/DefaultDailyRewardServiceTest.java
git commit -m "feat: add daily criterion progress and claims"
```

### Task 6: Implement daily/weekly leaderboards

**Files:**
- Create: `src/main/java/dev/mintychochip/core/DefaultLeaderboardService.java`
- Test: `src/test/java/dev/mintychochip/core/DefaultLeaderboardServiceTest.java`

**Interfaces:**
- Constructor: `DefaultLeaderboardService(LeaderboardRepository, Clock)`.
- Daily rank = current UTC date totals by criterion activity. Weekly rank = current UTC ISO-week totals. Both return immutable top-N entries and viewer rank.

- [ ] **Step 1: Write failing leaderboard tests**

```java
@Test
void ranksDescendingWithDeterministicTieBreakAndSeparatesPeriods() {
  service.add(playerA, LeaderboardPeriod.DAILY, "2026-08-12", 5);
  service.add(playerB, LeaderboardPeriod.DAILY, "2026-08-12", 9);
  service.add(playerA, LeaderboardPeriod.WEEKLY, "2026-W33", 99);
  assertEquals(playerB, service.leaderboard(LeaderboardPeriod.DAILY, 10).entries().get(0).playerId());
  assertEquals(1, service.leaderboard(LeaderboardPeriod.WEEKLY, 10).entries().size());
}
```

- [ ] **Step 2: Run tests and observe failure**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultLeaderboardServiceTest`
Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement aggregate/query behavior**

Provide package-private `recordProgress` used by `DefaultDailyRewardService` and public immutable reads through `LeaderboardService`. Aggregate all activity totals for the current period, sort by descending score then `updatedAt` ascending then UUID, assign one-based ranks, cap at `limit` (1..100), and include the viewer rank when requested through the view factory. Daily/weekly window keys come from `DailyWindow`; no cross-server state.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultLeaderboardServiceTest`
Expected: PASS for ordering, tie-breaks, period isolation, limits, unknown state, persistence, and immutable views.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/core/DefaultLeaderboardService.java src/test/java/dev/mintychochip/core/DefaultLeaderboardServiceTest.java
git commit -m "feat: rank criterion progress in leaderboards"
```

### Task 7: Add Paper config and default rewards.yml

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/RewardsConfig.java`
- Create: `src/main/resources/rewards.yml`
- Test: `src/test/java/dev/mintychochip/paper/RewardsConfigTest.java`

**Interfaces:**
- `RewardsConfig.load(JavaPlugin)` copies the bundled file, parses the pool/default reward/streak milestones/provider settings, and returns immutable configuration.
- Invalid individual criteria are skipped with a warning; an empty pool uses a built-in login criterion.

- [ ] **Step 1: Write failing config tests**

```java
@Test
void parsesCriterionPoolAndDefaults() {
  RewardsConfig config = RewardsConfig.parse(yaml("criterion-pool", List.of(...)));
  assertEquals(CriterionKind.MINE_BLOCKS, config.criterionPool().get(0).kind());
  assertEquals(ProviderMode.OPTIONAL, config.providerMode());
}

@Test
void emptyPoolUsesSafeLoginFallback() { … }
```

- [ ] **Step 2: Run tests and observe failure**

Run: `./gradlew test --tests dev.mintychochip.paper.RewardsConfigTest`
Expected: FAIL because config/parser does not exist.

- [ ] **Step 3: Implement parser and bundled config**

Use `YamlConfiguration` only in Paper. Parse namespaced keys with `MaterialKey`; map optional per-criterion rewards to the fixed `daily-reward` default. Parse streak milestone rewards and `command-allowlist`. Copy `rewards.yml` on first enable without overwriting user edits. Expose `workflowz` mode/endpoint and bounded limits. Log each skipped entry with its path and reason.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.paper.RewardsConfigTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/paper/RewardsConfig.java src/main/resources/rewards.yml src/test/java/dev/mintychochip/paper/RewardsConfigTest.java
git commit -m "feat: configure rotating reward criteria"
```

### Task 8: Add optional workflowz criterion provider

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/WorkflowzCriterionProvider.java`
- Test: `src/test/java/dev/mintychochip/paper/WorkflowzCriterionProviderTest.java`

**Interfaces:**
- Constructor accepts endpoint, mode, connect/request timeout, max response bytes, and injectable `HttpClient`/transport for tests.
- `CompletableFuture<Optional<Criterion>> proposeAsync(CriterionProposalRequest)` never runs network work on the Paper thread.

- [ ] **Step 1: Write failing provider tests**

```java
@Test
void validStructuredResponseBecomesAValidatedCriterion() { … }

@Test
void executableFieldsMalformedResponsesAndOversizedBodiesFallBack() { … }
```

- [ ] **Step 2: Run tests and observe failure**

Run: `./gradlew test --tests dev.mintychochip.paper.WorkflowzCriterionProviderTest`
Expected: FAIL because the provider does not exist.

- [ ] **Step 3: Implement bounded workflowz transport**

POST schema version/date/pool summary as JSON using Java `HttpClient`; enforce 3-second connect, 8-second request, and 64 KiB response limits. Parse only the documented fields (`id`, `type`, `key`, `target`, `description`, optional reward); reject `command`, `run_command`, and `actions` keys anywhere. Build via `Criterion` factories so API validation is authoritative. `DISABLED` returns empty immediately; `OPTIONAL` catches all transport/decode failures and returns empty; `REQUIRED` logs/throws a typed `WorkflowzProviderException` while the Paper rotation layer still falls back to the pool rather than disabling the plugin.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests dev.mintychochip.paper.WorkflowzCriterionProviderTest`
Expected: PASS for valid, invalid, oversized, executable-field, unavailable, disabled, optional, and required modes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/paper/WorkflowzCriterionProvider.java src/test/java/dev/mintychochip/paper/WorkflowzCriterionProviderTest.java
git commit -m "feat: add optional workflowz criterion proposals"
```

### Task 9: Add Paper commands, event tracking, and plugin lifecycle wiring

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/RewardsCommand.java`
- Create: `src/main/java/dev/mintychochip/paper/RewardsListener.java`
- Modify: `src/main/java/dev/mintychochip/ExtrasPlugin.java`
- Modify: `src/main/resources/paper-plugin.yml`
- Modify: `src/test/java/dev/mintychochip/PluginDescriptorTest.java`
- Test: `src/test/java/dev/mintychochip/paper/RewardsCommandTest.java`

**Interfaces:**
- Register API services at `ServicePriority.Normal`.
- Register `/rewards` (aliases `daily`, `streak`, `leaderboard`) via `LifecycleEvents.COMMANDS`; subcommands: status, claim, top, streak, reroll, reload.
- `RewardsListener` maps successful `BlockBreakEvent`, `EntityDeathEvent`, `CraftItemEvent`, `PlayerExpChangeEvent`, `PlayerJoinEvent`, and bounded play-time ticks to `CriterionProgress`.

- [ ] **Step 1: Write failing command/descriptor tests**

```java
@Test
void descriptorDeclaresRewardPermissions() throws Exception {
  String yaml = descriptorText();
  assertTrue(yaml.contains("extras.rewards.use:"));
  assertTrue(yaml.contains("extras.rewards.admin:"));
}

@Test
void commandWithoutPlayerSenderReceivesPlayerOnlyMessage() { … }
```

- [ ] **Step 2: Run tests and observe failure**

Run: `./gradlew test --tests dev.mintychochip.PluginDescriptorTest --tests dev.mintychochip.paper.RewardsCommandTest`
Expected: FAIL because metadata/commands do not exist.

- [ ] **Step 3: Implement commands, listeners, and wiring**

`RewardsCommand` follows `TitleCommand`/`ChatCommand`: check `extras.rewards.use` for player actions and `extras.rewards.admin` for reroll/reload/admin mutations; suggestions expose only permitted subcommands and online players. Claim calls core, then Paper executes the returned validated `Reward` (inventory item, XP, or allow-listed command with `%player%` replacement). `RewardsListener` ignores cancelled events, maps Bukkit keys to `minecraft:<key>`, records login/streak synchronously, and uses Folia-safe player schedulers for play-time accumulation. Daily rotation starts from config pool, attempts workflowz asynchronously, then applies the validated result through the global region scheduler; failures choose the deterministic next pool entry. `ExtrasPlugin` creates `rewards.yml`, one `SqliteRewardStore`, repositories/services, provider/config/listener, registers services and command, and closes/unregisters every rewards object on disable. Make only additive edits to existing chat-channel changes.

Add `extras.rewards.use` (default true) and `extras.rewards.admin` (default op) to `paper-plugin.yml`; preserve existing description/commands and extend the descriptor test without replacing chat assertions.

- [ ] **Step 4: Run focused Paper tests**

Run: `./gradlew test --tests dev.mintychochip.PluginDescriptorTest --tests dev.mintychochip.paper.RewardsCommandTest`
Expected: PASS.

- [ ] **Step 5: Commit only reward-specific files**

```bash
git add src/main/java/dev/mintychochip/api/rewards src/main/java/dev/mintychochip/core src/main/java/dev/mintychochip/paper/RewardsCommand.java src/main/java/dev/mintychochip/paper/RewardsListener.java src/main/java/dev/mintychochip/paper/WorkflowzCriterionProvider.java src/main/java/dev/mintychochip/paper/RewardsConfig.java src/main/resources/rewards.yml src/main/resources/paper-plugin.yml src/main/java/dev/mintychochip/ExtrasPlugin.java src/test/java/dev/mintychochip/PluginDescriptorTest.java src/test/java/dev/mintychochip/api/rewards src/test/java/dev/mintychochip/core src/test/java/dev/mintychochip/paper
 git commit -m "feat: wire daily rewards, leaderboards, and streaks"
```

If existing unrelated uncommitted files would be included, stop before committing and leave the reward changes unstaged with an explicit file list; never include unrelated chat-channel work.

### Task 10: Update living spec and run full verification

**Files:**
- Create: `docs/living-specs/rewards.md`
- Modify: `src/test/java/dev/mintychochip/PluginDescriptorTest.java` only if final metadata assertions require it.

- [ ] **Step 1: Write the living spec**

Document API/core/paper boundaries, criterion kinds, UTC streak semantics, claim idempotency, leaderboard periods, workflowz optional/fallback safety, permissions, current/next/future status, and explicit non-goals.

- [ ] **Step 2: Run focused behavior tests**

Run: `./gradlew test --tests 'dev.mintychochip.api.rewards.*' --tests 'dev.mintychochip.core.*Reward*' --tests 'dev.mintychochip.core.*Streak*' --tests 'dev.mintychochip.paper.*Reward*'`
Expected: all new tests pass.

- [ ] **Step 3: Run the complete verification gate**

Run: `./gradlew clean check build`
Expected: tests, Spotless, Checkstyle, PMD, SpotBugs, shaded jar, API jars, and Javadocs all succeed.

- [ ] **Step 4: Smoke-test the changed runtime path**

Run: `./gradlew runServer` with a temporary `run/rewards.yml`/Paper server if available; join once, inspect `rewards.db`, run `/rewards`, `/streak`, and `/leaderboard`, then stop the server. If a real server cannot be launched, run the repository-backed service integration tests and record that limitation; do not claim a Paper smoke test.

- [ ] **Step 5: Review final state**

Confirm every objective deliverable has direct evidence: API criterion types, automatic event progress, daily rotation, claim payout and idempotency, daily/weekly ranking, streak increment/reset/best, workflowz optional proposal + deterministic fallback, config and permissions, tests, and green quality gates. Inspect `git status`; preserve unrelated user work.
