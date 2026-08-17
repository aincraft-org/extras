# MapGUI Social Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a MapGUI social menu to the Extras Paper plugin, opened by `/social`, `/friend list`, and `/party list`.

**Architecture:** A single MapGUI `SocialScreen` owns tabbed views (Friends, Party, Invites) and a player picker for friend/invite actions. `FriendCommand`, `PartyCommand`, and a new `SocialCommand` call `MapGui.get().open(player, new SocialScreen(...))`. Gradle uses compile-only `mapgui-api`; `paper-plugin.yml` declares MapGUI as a required server dependency.

**Tech Stack:** Java 25, Gradle Kotlin DSL, MapGUI 1.1.0, Paper API 1.21.11.

## Global Constraints

- `io.github.flog99:mapgui-api:1.1.0` is `compileOnly` and must **not** be shaded or relocated.
- Java toolchain must be Java 25 to compile/load MapGUI.
- `paper-plugin.yml` dependency: `MapGUI` load `BEFORE`, `required: true`, `join-classpath: true`.
- Console/non-player keep the existing `/friend list` and `/party list` text output.
- Do not change `FriendService` or `PartyService` contracts.
- Keep the plugin `folia-supported: true` flag unchanged.
- All new code must pass `checkstyle`, `pmd`, `spotbugs`, and `spotlessCheck`.
- Use MapGUI 1.1.0 for both the compile API and the runtime plugin jar.

---

### Task 1: Build metadata and dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/paper-plugin.yml`

**Interfaces:**
- Produces: `libs.mapgui.api`, Java 25 toolchain, `MapGUI` plugin dependency, `/social` command permission.

- [ ] **Step 1: Add `mapgui-api` to the version catalog**

```toml
[libraries]
mapgui-api = { module = "io.github.flog99:mapgui-api", version = "1.1.0" }
```

- [ ] **Step 2: Add compile-only dependency and bump Java toolchain**

```kotlin
dependencies {
    compileOnly(libs.mapgui.api)
    // ... existing
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

- [ ] **Step 3: Declare the MapGUI plugin dependency in `paper-plugin.yml`**

Append:

```yaml
dependencies:
  server:
    MapGUI:
      load: BEFORE
      required: true
      join-classpath: true
```

- [ ] **Step 4: Add `/social` command and permission**

```yaml
commands:
  social:
    description: Open the MapGUI social menu.
    permission: extras.social.use
    permission-message: You do not have permission to use the social menu.

permissions:
  extras.social.use:
    description: Open the MapGUI social menu
    default: true
```

- [ ] **Step 5: Verify**

Run: `./gradlew dependencies --configuration compileClasspath | grep flog99`
Expected: `io.github.flog99:mapgui-api:1.1.0` listed.

Run: `./gradlew compileJava`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts src/main/resources/paper-plugin.yml
git commit -m "build: add MapGUI 1.1.0 compileOnly dependency, Java 25, and plugin descriptor"
```

---

### Task 2: Command infrastructure and plugin wiring

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/SocialTab.java`
- Create: `src/main/java/dev/mintychochip/paper/PlayerPickerMode.java`
- Create: `src/main/java/dev/mintychochip/paper/SocialCommand.java`
- Modify: `src/main/java/dev/mintychochip/ExtrasPlugin.java`

**Interfaces:**
- Consumes: `FriendService`, `PartyService`, `MapGui.get()`.
- Produces: `/social` command; `SocialTab` and `PlayerPickerMode` enums.

- [ ] **Step 1: Write `SocialTab` and `PlayerPickerMode` enums**

`SocialTab`: `FRIENDS`, `PARTY`, `INVITES`, `PICKER`.
`PlayerPickerMode`: `ADD_FRIEND`, `INVITE_TO_PARTY`.

- [ ] **Step 2: Write `SocialCommand`**

A `BasicCommand` that rejects non-players, selects the default tab (Party if in a party, otherwise Friends), and calls `MapGui.get().open(player, new SocialScreen(friendService, partyService, tab))`. If `MapGui.get()` is null, send an unavailable message.

- [ ] **Step 3: Register `SocialCommand` in `ExtrasPlugin`**

Add inside `LifecycleEvents.COMMANDS`:

```java
event
    .registrar()
    .register(
        "social",
        "Open the MapGUI social menu.",
        List.of(),
        new SocialCommand(friendService, partyService));
```

- [ ] **Step 4: Verify**

Run: `./gradlew compileJava`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/paper/SocialTab.java \
        src/main/java/dev/mintychochip/paper/PlayerPickerMode.java \
        src/main/java/dev/mintychochip/paper/SocialCommand.java \
        src/main/java/dev/mintychochip/ExtrasPlugin.java
git commit -m "feat: add /social command and MapGUI wiring"
```

---

### Task 3: `SocialScreen` skeleton and tab bar

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/SocialScreen.java`
- Modify: `src/main/java/dev/mintychochip/paper/FriendCommand.java`
- Modify: `src/main/java/dev/mintychochip/paper/PartyCommand.java`

**Interfaces:**
- Consumes: `SocialTab`, `PlayerPickerMode`, `FriendService`, `PartyService`.
- Produces: `SocialScreen` opens for `/friend list`, `/party list`, and `/social`.

- [ ] **Step 1: Create `SocialScreen`**

Extend `de.flog99.mapgui.Screen`.
Use `State<SocialTab>` for `activeTab` and `previousTab`, `State<PlayerPickerMode>` for `pickerMode`.
Override `title()`, `hand()`, `clampPitch()` (return `false`), and `activateOn()` (return `Click.RIGHT`).
In `onOpen()`, call `refresh()`.

- [ ] **Step 2: Implement tab bar and body routing**

`topBar()` returns the three tab buttons (`Friends`, `Party`, `Invites`) or a picker `Back` bar when `pickerMode` is set.
`body()` switches on `activeTab` and `pickerMode` to call `buildFriends()`, `buildParty()`, `buildInvites()`, or `buildPicker()`.

- [ ] **Step 3: Wire `FriendCommand.list()` and `PartyCommand.list()`**

If the sender is a `Player` and `MapGui.get()` is non-null, open `SocialScreen` on the matching tab.
Keep the existing text output for console and as a fallback.

- [ ] **Step 4: Verify**

Run: `./gradlew compileJava`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/mintychochip/paper/SocialScreen.java \
        src/main/java/dev/mintychochip/paper/FriendCommand.java \
        src/main/java/dev/mintychochip/paper/PartyCommand.java
git commit -m "feat: add SocialScreen skeleton, tab bar, and command hooks"
```

---

### Task 4: Friends, Invites, Party tabs, and player picker

**Files:**
- Modify: `src/main/java/dev/mintychochip/paper/SocialScreen.java`

**Interfaces:**
- Consumes: `FriendService`, `PartyService`, `PlayerIds`, `Bukkit`.
- Produces: Tab content, action handlers, and the player picker.

- [ ] **Step 1: Add data fields and `refresh()`**

Load `friends`, `incoming`/`outgoing` friend requests, `currentParty`, and `partyInvites` into local fields in `onOpen()` and after every mutation. Call `refresh()` then `invalidate()` on success.

- [ ] **Step 2: Implement `buildFriends()`**

Header with `+ Add` button; list of friend rows (name colored green/gray, `Remove` button); pending request sections with `Accept`/`Decline`/`Cancel`.

- [ ] **Step 3: Implement `buildInvites()`**

Combined list of friend requests and party invites with the same accept/decline/cancel actions.

- [ ] **Step 4: Implement `buildParty()`**

If in a party: header with party name, `Invite`, `Disband` (leader), and member rows with leader star plus leader-only `Kick` / `Transfer` (or `Leave` for self).
If no party: `Create Party` with an optional `Field` for the name and a `Create` button, plus pending party invites.

- [ ] **Step 5: Implement `buildPicker()`**

A scrollable list of online players filtered by `pickerMode`:
- `ADD_FRIEND`: exclude self, existing friends, and pending-in-either-direction.
- `INVITE_TO_PARTY`: exclude self, players in a party, and already-invited to this party.
Each row has `Add` or `Invite` button. `Back` restores `previousTab`.

- [ ] **Step 6: Action handlers and feedback**

Call `friendService` and `partyService` methods. On success, call `refresh()` and `invalidate()`. On failure, call `player().sendActionBar(Component.text(resultDescription, NamedTextColor.RED))`.

- [ ] **Step 7: Verify build and format**

Run: `./gradlew compileJava spotlessCheck`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/mintychochip/paper/SocialScreen.java
git commit -m "feat: add friends, party, invites, and player picker"
```

---

### Task 5: Testing, quality gates, and docs

**Files:**
- Create: `src/test/java/dev/mintychochip/paper/SocialScreenTest.java` (optional if testable logic can be extracted)
- Modify: `docs/living-specs/friends.md`
- Modify: `docs/living-specs/parties.md`
- Modify: `src/main/java/dev/mintychochip/paper/SocialScreen.java` (as needed)

**Interfaces:**
- Consumes: complete code.
- Produces: green build and updated living specs.

- [ ] **Step 1: Run the full build**

```bash
./gradlew build
```

Expected: PASS, including `compileJava`, `test`, `checkstyle`, `pmd`, `spotbugs`, and `spotlessCheck`.

- [ ] **Step 2: Add focused tests if possible**

If pure helper methods (e.g., picker filtering) can be extracted as package-private static methods, add unit tests. Otherwise, rely on the `runServer` smoke test.

- [ ] **Step 3: Smoke test**

1. Download `MapGUI-1.1.0.jar` from `https://github.com/FloG99/MapGUI/releases/tag/v1.1.0` into `run/plugins/`.
2. Run: `./gradlew runServer`
3. As an op, run `/friend list`, `/party list`, and `/social`.
4. Verify tab switching, friend add, request accept/decline, party create/invite/kick/leave, and player picker.

- [ ] **Step 4: Update living specs**

In `docs/living-specs/friends.md` and `docs/living-specs/parties.md`, mark the GUI as completed under `Current`.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/mintychochip/paper/SocialScreenTest.java \
        docs/living-specs/friends.md \
        docs/living-specs/parties.md
git commit -m "test: add social menu tests and update living specs"
```

- [ ] **Step 6: Final verification**

Run: `./gradlew build`
Expected: PASS.
