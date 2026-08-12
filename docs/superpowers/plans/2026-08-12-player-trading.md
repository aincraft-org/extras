# Player Trading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe item-for-item `/trade <player>` workflow to the Paper plugin.

**Architecture:** A Bukkit-free in-memory `TradeService` owns request and confirmation state. A Paper `TradeGui` owns temporary inventory views, item return, and completion validation. `TradeCommand` starts requests and `ExtrasPlugin` wires the service, command, and listener lifecycle.

**Tech Stack:** Java 21, Paper API 1.21.11, JUnit 5, Gradle, existing BasicCommand and inventory listener patterns.

## Global Constraints

- Trading is item-for-item only; no economy or arbitrary asset support.
- Active offers are transient and are not persisted across restarts.
- API/core code must not reference Bukkit classes or `ItemStack`.
- Every offer mutation resets both confirmations.
- Completion must validate both participants, confirmations, and current offer versions before removing items.
- Cancellation returns offered items to their owners and drops only inventory overflow.
- Preserve existing command, service, formatting, and test conventions.

---

### Task 1: Add the Bukkit-free trade contract and service state machine

**Files:**
- Create: `src/main/java/dev/mintychochip/api/TradeResult.java`
- Create: `src/main/java/dev/mintychochip/api/TradeSnapshot.java`
- Create: `src/main/java/dev/mintychochip/api/TradeService.java`
- Create: `src/main/java/dev/mintychochip/core/DefaultTradeService.java`
- Test: `src/test/java/dev/mintychochip/core/DefaultTradeServiceTest.java`

**Interfaces:**
- `TradeService.request(UUID requesterId, UUID targetId): TradeResult`
- `TradeService.accept(UUID playerId): TradeResult`
- `TradeService.decline(UUID playerId): TradeResult`
- `TradeService.cancel(UUID playerId): TradeResult`
- `TradeService.confirm(UUID playerId, long offerVersion): TradeResult`
- `TradeService.complete(UUID playerId, long firstOfferVersion, long secondOfferVersion): TradeResult`
- `TradeService.pendingRequestFrom(UUID playerId): Optional<UUID>`
- `TradeService.tradeOf(UUID playerId): Optional<TradeSnapshot>`
- `TradeSnapshot` exposes trade id, both participant IDs, both offer versions, and both confirmation flags.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void acceptsRequestAndRequiresBothCurrentConfirmations() {
  UUID alice = UUID.randomUUID();
  UUID bob = UUID.randomUUID();
  TradeService service = new DefaultTradeService();

  assertEquals(TradeResult.SUCCESS, service.request(alice, bob));
  assertEquals(TradeResult.SUCCESS, service.accept(bob));
  TradeSnapshot trade = service.tradeOf(alice).orElseThrow();

  assertEquals(TradeResult.SUCCESS, service.confirm(alice, trade.offerVersionOf(alice)));
  assertEquals(TradeResult.NOT_CONFIRMED, service.complete(alice, trade.offerVersionOf(alice), trade.offerVersionOf(bob)));
  assertEquals(TradeResult.SUCCESS, service.confirm(bob, trade.offerVersionOf(bob)));
  assertEquals(TradeResult.SUCCESS, service.complete(alice, trade.offerVersionOf(alice), trade.offerVersionOf(bob)));
}

@Test
void offerVersionChangeInvalidatesBothConfirmations() {
  UUID alice = UUID.randomUUID();
  UUID bob = UUID.randomUUID();
  TradeService service = new DefaultTradeService();
  service.request(alice, bob);
  service.accept(bob);
  TradeSnapshot before = service.tradeOf(alice).orElseThrow();

  assertEquals(TradeResult.SUCCESS, service.confirm(alice, before.offerVersionOf(alice)));
  assertEquals(TradeResult.SUCCESS, service.confirm(bob, before.offerVersionOf(bob)));
  service.offerChanged(alice);

  TradeSnapshot after = service.tradeOf(alice).orElseThrow();
  assertTrue(!after.confirmed(alice) && !after.confirmed(bob));
  assertTrue(after.offerVersionOf(alice) > before.offerVersionOf(alice));
}
```

- [ ] **Step 2: Run the focused test and verify the expected missing-API failure**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultTradeServiceTest`
Expected: FAIL because the trade API and implementation do not exist yet.

- [ ] **Step 3: Implement immutable result/snapshot types and synchronized state transitions**

Use a single private lock and maps keyed by participant UUID. Reject self-requests, duplicate requests, participants already in an active trade, and stale confirmation versions. `offerChanged(UUID)` increments that participant's version and clears both confirmations. Completion removes the active trade only after both confirmations and exact versions match.

- [ ] **Step 4: Run focused tests and verify they pass**

Run: `./gradlew test --tests dev.mintychochip.core.DefaultTradeServiceTest`
Expected: PASS with all focused trade service tests passing.

- [ ] **Step 5: Commit the service contract and implementation**

```bash
git add src/main/java/dev/mintychochip/api/TradeResult.java src/main/java/dev/mintychochip/api/TradeSnapshot.java src/main/java/dev/mintychochip/api/TradeService.java src/main/java/dev/mintychochip/core/DefaultTradeService.java src/test/java/dev/mintychochip/core/DefaultTradeServiceTest.java
git commit -m "feat: add item trade service state machine"
```

### Task 2: Add the Paper trade GUI and item-safety helpers

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/TradeGui.java`
- Test: `src/test/java/dev/mintychochip/paper/TradeGuiTest.java` only for Bukkit-free helper behavior that can run headlessly.

**Interfaces:**
- `TradeGui.openRequest(Player requester, Player target, TradeService service)`
- `TradeGui.listener(TradeService service): Listener`
- `TradeGui.closeActiveSessions(): void`

- [ ] **Step 1: Write failing helper tests**

```java
@Test
void offerSlotsArePartitionedByViewer() {
  assertTrue(TradeGui.isOfferSlotFor(0, 0));
  assertFalse(TradeGui.isOfferSlotFor(0, 27));
  assertTrue(TradeGui.isOfferSlotFor(1, 27));
  assertFalse(TradeGui.isOfferSlotFor(1, 0));
}
```

- [ ] **Step 2: Run the focused GUI test and verify it fails for the missing helper**

Run: `./gradlew test --tests dev.mintychochip.paper.TradeGuiTest`
Expected: FAIL because `TradeGui` does not exist yet.

- [ ] **Step 3: Implement sessions, inventory layout, click/drag handlers, and cancellation**

Use a stable title prefix, a session map keyed by trade ID, mirrored inventories for both viewers, explicit offer slot ranges, and confirmation controls. Cancel top-inventory clicks outside the current viewer's offer slots and confirmation controls. Cancel confirmation on offer mutations. On close or plugin disable, remove session state and return each side's items with `Player.getInventory().addItem`, dropping leftovers at the owner location.

- [ ] **Step 4: Implement completion validation and overflow handling**

Require both online participants, both confirmed flags, and matching service offer versions. Clone offer stacks before removal. Remove only expected offer slots, call `addItem` on the opposite player, and drop leftovers. If service completion rejects, leave items in place and clear confirmations through the service.

- [ ] **Step 5: Run focused GUI tests and verify they pass**

Run: `./gradlew test --tests dev.mintychochip.paper.TradeGuiTest`
Expected: PASS; headless Bukkit-dependent tests may be skipped according to existing `MailboxItemCodecTest` conventions.

- [ ] **Step 6: Commit the GUI implementation**

```bash
git add src/main/java/dev/mintychochip/paper/TradeGui.java src/test/java/dev/mintychochip/paper/TradeGuiTest.java
git commit -m "feat: add safe player trade inventory GUI"
```

### Task 3: Add `/trade` command and plugin lifecycle wiring

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/TradeCommand.java`
- Modify: `src/main/java/dev/mintychochip/ExtrasPlugin.java`
- Modify: `src/main/resources/paper-plugin.yml`
- Test: `src/test/java/dev/mintychochip/PluginDescriptorTest.java` if descriptor assertions need updating.

**Interfaces:**
- `TradeCommand(TradeService service)` implements `BasicCommand`.
- `/trade <player>` sends a request, resolves online target using `PlayerIds`, and opens the GUI after target acceptance.
- `/trade accept|decline|cancel` handles the latest request or current trade.

- [ ] **Step 1: Add command behavior tests or descriptor assertions before implementation**

Assert the descriptor description includes trading and the command registration contract includes `trade` and its usage text.

- [ ] **Step 2: Run the focused plugin test and verify the new assertions fail**

Run: `./gradlew test --tests dev.mintychochip.PluginDescriptorTest`
Expected: FAIL until descriptor and registration are updated.

- [ ] **Step 3: Implement `TradeCommand`**

Mirror `FriendCommand`/`PartyCommand`: require a player, validate target argument, reject self/unknown targets through service results, notify the target, and suggest online player names excluding the sender. Implement explicit usage output.

- [ ] **Step 4: Wire service, command, listener, and shutdown cleanup in `ExtrasPlugin`**

Construct `DefaultTradeService`, register `trade` in `LifecycleEvents.COMMANDS`, register `TradeGui.listener`, close active sessions in `onDisable`, and update the enable/disable log and plugin description.

- [ ] **Step 5: Run focused plugin tests and verify they pass**

Run: `./gradlew test --tests dev.mintychochip.PluginDescriptorTest`
Expected: PASS.

- [ ] **Step 6: Commit command and lifecycle wiring**

```bash
git add src/main/java/dev/mintychochip/paper/TradeCommand.java src/main/java/dev/mintychochip/ExtrasPlugin.java src/main/resources/paper-plugin.yml src/test/java/dev/mintychochip/PluginDescriptorTest.java
git commit -m "feat: register the trade command"
```

### Task 4: Run full verification and Paper smoke checks

**Files:**
- Modify only files required by failing verification.

- [ ] **Step 1: Run the full quality gate**

Run: `./gradlew check`
Expected: PASS with tests, Spotless, Checkstyle, PMD, and SpotBugs clean.

- [ ] **Step 2: Build the shaded plugin artifacts**

Run: `./gradlew build`
Expected: PASS and produce the existing API and Paper artifacts under `build/libs`.

- [ ] **Step 3: Run a Paper smoke test**

Run: `./gradlew runServer`, then connect two test players and exercise `/trade <player>`, item placement, confirmation, cancellation, and successful exchange. Verify the server log has no trade-related exceptions and both inventories contain the opposite offers after completion.

- [ ] **Step 4: Commit only verification-driven fixes**

```bash
git add <exact-files-changed-by-verification>
git commit -m "fix: address trade verification findings"
```
