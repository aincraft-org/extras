# Chat Channels and Item Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build persistent Global, Local, Party, Market, and LFG chat with Adventure formatting and `%item` hover links.

**Architecture:** A Bukkit-free `ChatService` persists player preferences and enforces channel invariants. Paper adapters maintain Folia-safe presence snapshots, route `AsyncChatEvent` viewers, render Adventure components, and capture held items through the sender entity scheduler.

**Tech Stack:** Java 21, Paper API 1.21.11, Adventure components, SQLite JDBC, JUnit 5, Gradle.

## Global Constraints

- Channels are exactly Global, Local, Party, Market, and LFG.
- Local radius is 100 blocks inclusive and same-world only.
- Active and muted channel preferences persist in `chat.db`.
- Global cannot be muted; selecting a channel unmutes it.
- `%item` is case-insensitive, supports one token, and `%%item` escapes it.
- Empty-hand `%item` rejects the message.
- No price estimates or LLM integration in this release.
- No direct Bukkit world, location, or inventory reads on asynchronous chat threads.
- Global cooldown is 2 seconds; Local/Party 1 second; Market/LFG 5 seconds.
- Maximum trimmed message length is 256 Unicode code points.

---

### Task 1: Add persistent chat preferences

**Files:**
- Create: `src/main/java/dev/mintychochip/api/ChannelId.java`
- Create: `src/main/java/dev/mintychochip/api/ChannelPreferences.java`
- Create: `src/main/java/dev/mintychochip/api/ChatResult.java`
- Create: `src/main/java/dev/mintychochip/api/ChatService.java`
- Create: `src/main/java/dev/mintychochip/core/ChatRepository.java`
- Create: `src/main/java/dev/mintychochip/core/SqliteChatRepository.java`
- Create: `src/main/java/dev/mintychochip/core/DefaultChatService.java`
- Test: `src/test/java/dev/mintychochip/core/DefaultChatServiceTest.java`
- Test: `src/test/java/dev/mintychochip/core/SqliteChatRepositoryTest.java`

**Interfaces:**
- `ChannelId.parse(String): Optional<ChannelId>` with stable lowercase keys.
- `ChannelPreferences(UUID playerId, ChannelId activeChannel, Set<ChannelId> mutedChannels)` immutable snapshot.
- `ChatService.preferences(UUID): ChannelPreferences`.
- `ChatService.select(UUID, ChannelId): ChatResult`.
- `ChatService.mute(UUID, ChannelId): ChatResult`.
- `ChatService.unmute(UUID, ChannelId): ChatResult`.

- [ ] Write tests asserting absent users default to Global, preferences survive repository reopen, selecting persists and unmutes, Global mute is rejected, and returned mute sets are immutable.
- [ ] Run `./gradlew test --tests dev.mintychochip.core.DefaultChatServiceTest --tests dev.mintychochip.core.SqliteChatRepositoryTest`; expect missing-type failures.
- [ ] Implement the API, SQLite schema/repository, and synchronized cached service.
- [ ] Re-run the focused tests; expect PASS.
- [ ] Commit as `feat: add persistent chat preferences`.

### Task 2: Add pure routing and validation

**Files:**
- Create: `src/main/java/dev/mintychochip/api/ChatMessage.java`
- Create: `src/main/java/dev/mintychochip/api/ChatDelivery.java`
- Create: `src/main/java/dev/mintychochip/core/ChatRouter.java`
- Test: `src/test/java/dev/mintychochip/core/ChatRouterTest.java`

**Interfaces:**
- `ChatMessage` holds sender UUID, channel, trimmed plain text, and creation instant.
- `ChatDelivery` holds accepted result and immutable recipient UUID set.
- `ChatRouter.route(ChatMessage, PresenceSnapshot, Collection<PresenceSnapshot>, Optional<Party>, Function<UUID,ChannelPreferences>): ChatDelivery`.
- Local distance comparison uses squared distance `<= 10_000` and exact world UUID equality.

- [ ] Write failing tests for all five channels, Local 99.9/100/100.1 boundaries, cross-world exclusion, Party rejection/membership, muted recipient filtering, sender inclusion, empty text, and 257-code-point rejection.
- [ ] Run the focused test and verify failures occur because routing types are absent.
- [ ] Implement immutable presence/message/delivery values and pure routing.
- [ ] Re-run focused tests; expect PASS.
- [ ] Commit as `feat: add chat channel routing`.

### Task 3: Add item token parsing and Adventure rendering

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/ItemLinkParser.java`
- Create: `src/main/java/dev/mintychochip/paper/ChatFormatter.java`
- Test: `src/test/java/dev/mintychochip/paper/ItemLinkParserTest.java`
- Test: `src/test/java/dev/mintychochip/paper/ChatFormatterTest.java`

**Interfaces:**
- `ItemLinkParser.parse(String): ItemLinkParseResult` distinguishes no token, one token, escaped literal, and too many tokens.
- `ChatFormatter.format(ChannelId, Component title, Component displayName, Component message, ItemStack heldItem): Component`.
- Item links render `[effective item name xN]` with `heldItem.clone().asHoverEvent()`.

- [ ] Write failing parser tests for `%item`, `%ITEM`, `%%item`, two tokens, and surrounding text; add formatter tests for five labels/colors and optional title.
- [ ] Run focused tests; expect missing-type failures.
- [ ] Implement parser and formatter without mutating the held item.
- [ ] Re-run focused tests; headless Bukkit item tests may use the existing skip convention when server APIs are unavailable.
- [ ] Commit as `feat: render chat item links`.

### Task 4: Add Folia-safe presence and chat listeners

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/PresenceSnapshot.java`
- Create: `src/main/java/dev/mintychochip/paper/ChatPresenceRegistry.java`
- Create: `src/main/java/dev/mintychochip/paper/ChatListener.java`
- Test: `src/test/java/dev/mintychochip/paper/ChatPresenceRegistryTest.java`

**Interfaces:**
- Registry returns immutable UUID-keyed snapshots and updates from join/quit/teleport/world-change events.
- Listener receives `ChatService`, `PartyService`, `TitleService`, `ChatRouter`, and registry.
- Held-item capture uses `player.getScheduler().execute(...)` and a bounded future; timeout rejects `%item`.
- The listener reads registry/service snapshots only from asynchronous paths and filters mutable event viewers before installing the renderer.

- [ ] Write failing registry snapshot tests and any pure listener decision tests possible without a server.
- [ ] Run focused tests; expect missing-type failures.
- [ ] Implement registry and listener with one listener instance registered by the plugin.
- [ ] Re-run focused tests; expect PASS.
- [ ] Commit as `feat: route Paper chat channels`.

### Task 5: Add `/chat` and plugin lifecycle wiring

**Files:**
- Create: `src/main/java/dev/mintychochip/paper/ChatCommand.java`
- Modify: `src/main/java/dev/mintychochip/ExtrasPlugin.java`
- Modify: `src/main/resources/paper-plugin.yml`
- Modify: `src/test/java/dev/mintychochip/PluginDescriptorTest.java`

**Interfaces:**
- `/chat <channel>`, `/chat send <channel> <message>`, `/chat mute`, `/chat unmute`, `/chat status`, `/chat channels`, `/chat help`.
- Aliases `/ch` and `/c`.
- Plugin owns chat repository close and listener unregister lifecycle.

- [ ] Add failing descriptor assertions for chat permissions and plugin description.
- [ ] Run `./gradlew test --tests dev.mintychochip.PluginDescriptorTest`; expect failure.
- [ ] Implement `ChatCommand`, register aliases/listeners/service, close resources, and update descriptor.
- [ ] Re-run focused tests; expect PASS.
- [ ] Commit as `feat: register chat channels`.

### Task 6: Verify and push

**Files:**
- Modify only files required by concrete verification failures.

- [ ] Run `./gradlew spotlessApply`.
- [ ] Run `./gradlew check`; expect all tests and quality tools to pass.
- [ ] Run `./gradlew build`; expect API/Paper jars and javadocs to build.
- [ ] Start `./gradlew runServer`, verify plugin enable without chat-related exceptions, then stop cleanly. Multi-player routing and hover inspection remain a live-client smoke requirement when test clients are available.
- [ ] Run final workflowz adversarial review for correctness, Folia/thread safety, and item-link security; fix every Critical/Important finding and rerun covering tests.
- [ ] Inspect `git status`, staged diffs, and remote/branch; ensure the worktree is clean.
- [ ] Push the current branch without force and verify the remote branch contains the new commits.
