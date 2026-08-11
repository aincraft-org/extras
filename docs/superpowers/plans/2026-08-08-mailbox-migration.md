# Modular Mailbox Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold the standalone mailbox API, persistence, command, and GUIs into `../modular-extras` while making `ExtrasPlugin` the sole Paper entrypoint.

**Architecture:** Preserve the mailbox layers but map them onto the destination’s single-module packages: `dev.jlo.extras.api` for Bukkit-free types, `dev.jlo.extras.core` for services and SQLite storage, and `dev.jlo.extras.paper` for command/GUI code. `ExtrasPlugin` owns the mailbox repository and service, registers `/mail` with its existing Paper lifecycle registrar, and closes the repository during disable.

**Tech Stack:** Java 21, Paper API 1.21.11, Gradle 9.6.1, JUnit 5, SQLite JDBC 3.53.2.1, Paper lifecycle `BasicCommand` registration, Shadow.

## Global Constraints

- Leave `/home/jlo/dev/modular-mailbox` untouched.
- Do not initialize Git or stage/commit files because `../modular-extras` has no `.git` directory and is an untracked project under `/home/jlo/dev`.
- Keep `dev.jlo.extras.ExtrasPlugin` as the only `paper-plugin.yml` main class.
- Preserve the mailbox API signatures, validation rules, SQL schema, attachment encoding, command names, permission `modular.mail.use`, and GUI behavior.
- Store mailbox data at `<ExtrasPlugin data directory>/mailbox/mailbox.db`.
- Do not refactor party, friend, or title implementations beyond the lifecycle additions required to host mailbox.
- Do not add a separate Gradle subproject or descriptor for mailbox.

---

### Task 1: Port mailbox API and core persistence

**Files:**
- Create: `src/main/java/dev/jlo/extras/api/MailMessage.java`
- Create: `src/main/java/dev/jlo/extras/api/MailboxView.java`
- Create: `src/main/java/dev/jlo/extras/api/MailService.java`
- Create: `src/main/java/dev/jlo/extras/api/SendMailResult.java`
- Create: `src/main/java/dev/jlo/extras/core/MailRepository.java`
- Create: `src/main/java/dev/jlo/extras/core/DefaultMailService.java`
- Create: `src/main/java/dev/jlo/extras/core/SqliteMailRepository.java`
- Create: `src/test/java/dev/jlo/extras/core/DefaultMailServiceTest.java`
- Create: `src/test/java/dev/jlo/extras/core/SqliteMailRepositoryTest.java`

**Interfaces:**
- `MailService.send(UUID, UUID, String, String, String)` returns `SendMailResult`.
- `MailService.mailbox(UUID, int, int)` returns immutable `MailboxView`.
- `MailService.markRead`, `markUnread`, `claimAttachment`, `delete`, `deleteAllRead`, and `unreadCount` retain their existing signatures.
- `MailRepository` remains the storage seam consumed by `DefaultMailService`.
- `SqliteMailRepository(Path)` creates the parent directory and the existing `mail` table/index schema, and `close()` releases its single JDBC connection.

- [ ] **Step 1: Copy the four API types into destination package names.**

  Preserve the record fields and public method contracts exactly. Change only
  the package declaration from `dev.jlo.mailbox.api` to
  `dev.jlo.extras.api`. `MailboxView` must continue to defensively copy and
  expose an unmodifiable message list.

- [ ] **Step 2: Copy the repository/service types into `dev.jlo.extras.core`.**

  Change imports from `dev.jlo.mailbox.api.*` to `dev.jlo.extras.api.*`.
  Keep the repository schema fields `id`, `recipient`, `sender_name`, `body`,
  `sent_at`, `read`, `claimed`, and `attachment`; keep newest-first ordering
  by `sent_at DESC, id DESC`; keep recipient-qualified mutations; and keep the
  transaction around attachment claim.

- [ ] **Step 3: Port the two core test classes with destination packages/imports.**

  Change test packages to `dev.jlo.extras.core` and API imports to
  `dev.jlo.extras.api`. Keep coverage for send validation/trimming, self-mail,
  attachment passthrough, page-size clamping, read/unread state, claim-once
  semantics, recipient scoping, deletion rules, and persistence across reopen.

- [ ] **Step 4: Run the focused core tests.**

  Run from `../modular-extras`:

  ```text
  ./gradlew test --tests 'dev.jlo.extras.core.*'
  ```

  Expected result: all migrated core tests pass, with no compilation errors
  from existing destination tests.

---

### Task 2: Port mailbox Paper command, GUIs, codec, and tests

**Files:**
- Create: `src/main/java/dev/jlo/extras/paper/MailCommand.java`
- Create: `src/main/java/dev/jlo/extras/paper/MailboxGui.java`
- Create: `src/main/java/dev/jlo/extras/paper/ComposeGui.java`
- Create: `src/main/java/dev/jlo/extras/paper/MailboxItemCodec.java`
- Create: `src/test/java/dev/jlo/extras/paper/MailboxItemCodecTest.java`

**Interfaces:**
- `MailCommand` implements Paper `BasicCommand` and accepts a `MailService` in its constructor.
- `MailboxGui.open(Player, MailService)` opens page zero; `MailboxGui.clickListener(MailService)` returns the registered listener.
- `ComposeGui.open(Player, MailService, UUID, String, String)` opens a compose session; `ComposeGui.listener()` returns the registered listener.
- `MailboxItemCodec.encode(ItemStack)` returns a nullable opaque blob; `decode(String)` returns `Optional<ItemStack>`; `hasAttachment(ItemStack)` reports attachable stacks.

- [ ] **Step 1: Copy Paper classes and update package/import declarations.**

  Change package declarations to `dev.jlo.extras.paper` and API imports to
  `dev.jlo.extras.api`. Preserve the `modular.mail.use` permission, command
  subcommands (`open`, `send`, `delete`, `clear`), player resolution rules,
  GUI slot behavior, attachment decode-before-claim safety, and YAML codec
  format discriminator `1`.

- [ ] **Step 2: Remove the standalone registration helper from `MailCommand`.**

  Remove its `LifecycleEvents` and `JavaPlugin` imports and remove the static
  `register(JavaPlugin, MailService)` method. The command remains a plain
  `BasicCommand`; `ExtrasPlugin` will own lifecycle registration so no second
  command handler is created.

- [ ] **Step 3: Update lifecycle Javadocs.**

  Replace `MailboxPlugin` references in `MailCommand`, `MailboxGui`, and
  `ComposeGui` with `ExtrasPlugin`, without changing runtime behavior.

- [ ] **Step 4: Port `MailboxItemCodecTest`.**

  Change its package to `dev.jlo.extras.paper`; retain the existing headless
  Bukkit assumption and live-server round-trip assertions for basic, enchanted,
  named, and stacked items plus null/corrupt/air handling.

- [ ] **Step 5: Run focused Paper compilation/tests.**

  Run from `../modular-extras`:

  ```text
  ./gradlew test --tests 'dev.jlo.extras.paper.*'
  ```

  Expected result: codec tests pass or retain only their documented headless
  skips; all Paper sources compile against the destination Paper API.

---

### Task 3: Integrate mailbox into `ExtrasPlugin`

**Files:**
- Modify: `src/main/java/dev/jlo/extras/ExtrasPlugin.java`
- Modify: `src/main/resources/paper-plugin.yml`
- Modify: `src/test/java/dev/jlo/extras/PluginDescriptorTest.java`

**Interfaces:**
- `ExtrasPlugin` owns `SqliteMailRepository mailRepository` and
  `MailService mailService` fields.
- The lifecycle command registrar registers a `MailCommand(mailService)` under
  the literal command name `mail`.
- Plugin shutdown closes the mailbox repository and nulls mailbox state.

- [ ] **Step 1: Add mailbox imports and lifecycle fields.**

  Import `dev.jlo.extras.api.MailService`,
  `dev.jlo.extras.core.DefaultMailService`,
  `dev.jlo.extras.core.SqliteMailRepository`,
  `dev.jlo.extras.paper.ComposeGui`,
  `dev.jlo.extras.paper.MailCommand`, and
  `dev.jlo.extras.paper.MailboxGui`. Add:

  ```java
  private SqliteMailRepository mailRepository;
  private MailService mailService;
  ```

- [ ] **Step 2: Construct and register mailbox services during enable.**

  After `Path dataDir = getDataFolder().toPath();`, construct
  `new SqliteMailRepository(dataDir.resolve("mailbox").resolve("mailbox.db"))`
  and `new DefaultMailService(mailRepository)`. Register `MailService` with
  `Bukkit.getServicesManager()` at `ServicePriority.Normal`, then register
  `MailboxGui.clickListener(mailService)` and `ComposeGui.listener()` with the
  plugin. Keep all existing party/friend/title setup unchanged.

- [ ] **Step 3: Register `/mail` in the existing lifecycle command handler.**

  In the `LifecycleEvents.COMMANDS` callback, add this registration beside the
  existing three commands:

  ```java
  event.registrar().register(
          "mail", "Player mailbox — send, read, and claim mail.", List.of(),
          new MailCommand(mailService));
  ```

  Do not add a second `main` class or a separate command lifecycle callback.

- [ ] **Step 4: Close mailbox state during disable.**

  Before the final disabled log, close `mailRepository` when non-null, set it
  to `null`, set `mailService` to `null`, and retain the existing service
  unregistration. Ensure party/friend listener cleanup and service closure still
  run exactly once.

- [ ] **Step 5: Keep the destination descriptor single-entrypoint.**

  Ensure `paper-plugin.yml` contains `main: dev.jlo.extras.ExtrasPlugin` and no
  `dev.jlo.mailbox.paper.MailboxPlugin` main. Keep the destination descriptor’s
  existing `ModularExtras` metadata; do not restore the standalone `commands:`
  block because `/mail` is lifecycle-registered.

- [ ] **Step 6: Strengthen descriptor coverage.**

  Extend `PluginDescriptorTest` with an assertion that the descriptor does not
  contain `dev.jlo.mailbox.paper.MailboxPlugin`, while retaining the current
  main-class, API-version, and Folia assertions.

- [ ] **Step 7: Compile the integrated plugin.**

  Run from `../modular-extras`:

  ```text
  ./gradlew compileJava compileTestJava processResources
  ```

  Expected result: `ExtrasPlugin`, all mailbox classes, and all existing
  destination domains compile together; processed resources contain the
  `${version}` expansion and the single destination main class.

---

### Task 4: Full verification and migration cleanup

**Files:**
- Verify: all destination source/test/resource files created or modified above
- Verify: `docs/superpowers/specs/2026-08-08-mailbox-migration-design.md`
- Verify: `docs/superpowers/plans/2026-08-08-mailbox-migration.md`

- [ ] **Step 1: Run the full destination test suite.**

  Run from `../modular-extras`:

  ```text
  ./gradlew test
  ```

  Expected result: existing party/friend/title tests and migrated mailbox core
  tests pass; the codec tests only skip when no Bukkit server is available.

- [ ] **Step 2: Build the shaded plugin artifact.**

  Run from `../modular-extras`:

  ```text
  ./gradlew build
  ```

  Expected result: the destination build succeeds and produces the
  `modular-extras` shaded jar with the SQLite driver included.

- [ ] **Step 3: Inspect the packaged descriptor and main-class references.**

  Run from `../modular-extras`:

  ```text
  unzip -p build/libs/modular-extras-*.jar paper-plugin.yml
  ```

  Confirm the output names only `dev.jlo.extras.ExtrasPlugin` as `main` and
  contains no `MailboxPlugin` reference.

- [ ] **Step 4: Run the available Paper smoke path.**

  If the destination run configuration has a server jar, run its configured
  Paper task and confirm startup logs show `ModularExtras` enabling without a
  mailbox database error. Confirm shutdown completes after the mailbox database
  is opened. Do not alter the source repository’s server run directory.

- [ ] **Step 5: Check the destination diff scope.**

  Confirm only the mailbox migration files, `ExtrasPlugin`, descriptor tests,
  and the required design/plan docs were added or changed in the destination;
  leave all pre-existing untracked destination work intact. Report that no Git
  commit was created because the destination has no repository metadata.
