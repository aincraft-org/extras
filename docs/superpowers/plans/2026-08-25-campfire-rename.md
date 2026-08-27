# Rename to `campfire` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the GitHub repo `aincraft-org/extras` to `aincraft-org/campfire` and update all user-facing identity (README, Maven URL, POM metadata, living-specs) while preserving runtime storage (`plugins/Extras/`) and all technical IDs (artifact names, Java packages, permission nodes, command names).

**Architecture:** Preserve all ambient user work in a reversible stash (including the untracked README), commit the approved design spec separately, then implement the rename on a clean tree and commit only rename-owned changes. Afterward, extract the stashed README draft to a distinct backup path rather than popping it onto the now-tracked `README.md`. Restore any tracked WIP only after verifying it does not overlap the rename commit. `gh api` performs the remote rename; `git remote set-url` updates the local URL. `./gradlew clean check build` verifies the implementation before its commit.

**Tech Stack:** Gradle Kotlin DSL, Paper plugin (Java), GitHub Actions, GitHub Packages, `gh` CLI, Git stash.

## Global Constraints

- **Repo rename:** `aincraft-org/extras` → `aincraft-org/campfire`. Use `gh api -X PATCH repos/aincraft-org/extras -f name=campfire` (or the web UI if `gh` lacks repo-admin scope).
- **Local remote URL:** `https://github.com/aincraft-org/campfire.git` after the rename.
- **Plugin data folder stays `plugins/Extras/`.** `paper-plugin.yml` `name: Extras` is **not** changed. Do not bundle a data migration.
- **Technical IDs stay unchanged:** artifactIds `extras-api`/`extras-paper`, Java packages `dev.mintychochip.*`, permission nodes `extras.*`, command names, plugin main class `dev.mintychochip.ExtrasPlugin`.
- **Historical docs stay frozen:** `docs/superpowers/plans/*` and prior `docs/superpowers/specs/*` are records of past work and are not edited. Only the 8 `docs/living-specs/*.md` files are updated.
- **Separate spec and implementation commits:** the spec is its own commit; rename implementation is a later commit. The user's WIP is never included in either commit.
- **User WIP is reversible, not committed by this task:** stash tracked edits and untracked files with `git stash push -u`; restore tracked WIP only after checking it does not overlap the rename. Extract the stashed untracked README to a distinct backup path; never `git stash pop` it onto tracked `README.md`.
- **README Packages link is in scope:** update both the GitHub Packages badge target (`github.com/aincraft-org/extras/packages`) and the Maven repository endpoint (`maven.pkg.github.com/aincraft-org/extras`) to `aincraft-org/campfire`.

---

## File Structure

**Committed separately:**
- `docs/superpowers/specs/2026-08-25-campfire-rename-design.md` — approved design spec

**Modified in the rename implementation commit:**
- `README.md` — fresh rename-owned README written after stashing the user's untracked draft; H1, description, badges, Packages link, Maven endpoint, runtime path explanation
- `src/main/resources/paper-plugin.yml` — `website` only; `name: Extras` and existing description unchanged
- `settings.gradle.kts` — `rootProject.name`
- `build.gradle.kts` — POM `url`, scm `connection`/`developerConnection`/`url`, publishing Maven URL, publication displayNames
- `config/pmd/pmd.xml` — ruleset name
- `docs/living-specs/advancements.md`, `cinematics.md`, `events.md`, `friends.md`, `parties.md`, `rewards.md`, `titles.md`, `toast.md` — heading prefix

**Explicitly not committed by this task:**
- User's tracked edits to `build.gradle.kts` and `src/main/resources/paper-plugin.yml`
- User's untracked `README.md` draft; it is extracted later to `README.md.userdraft`
- Any historical plan/spec files
- Java sources, artifact IDs, package names, permission nodes, command names, plugin `name`

---

### Task 0: Capture the current tree and stash all user work

**Files:**
- No committed file changes.
- Stash: tracked modifications plus untracked `README.md`.

- [ ] **Step 1: Capture the current-tree safety snapshot**

Run:
```bash
git status --short
git diff --no-color -- build.gradle.kts src/main/resources/paper-plugin.yml
wc -l README.md
```
Expected current state:
- `M build.gradle.kts` with two `artifactDescription` hunks
- `M src/main/resources/paper-plugin.yml` with one `description:` hunk
- `?? README.md`, currently 120 lines

Save the output in the task transcript; do not edit those files before stashing.

- [ ] **Step 2: Stash all current user/ambient work reversibly**

Run:
```bash
git stash push -u -m "ambient WIP before campfire rename"
git status --short
```
Expected: stash succeeds and the working tree is clean. The stash contains the two tracked WIP files and untracked `README.md`.

- [ ] **Step 3: Verify the stash contains the expected paths**

Run:
```bash
git stash list -1
git stash show --stat --include-untracked stash@{0}
```
Expected: newest stash is `ambient WIP before campfire rename`; stat includes `build.gradle.kts`, `src/main/resources/paper-plugin.yml`, and `README.md`.

Do not drop this stash until Task 6 verifies extraction/restoration.

---

### Task 1: Commit the approved spec separately

**Files:**
- `docs/superpowers/specs/2026-08-25-campfire-rename-design.md`

- [ ] **Step 1: Verify the spec exists on the clean tree**

Run: `ls -la docs/superpowers/specs/2026-08-25-campfire-rename-design.md`
Expected: file exists.

- [ ] **Step 2: Stage only the spec**

Run:
```bash
git add docs/superpowers/specs/2026-08-25-campfire-rename-design.md
git diff --cached --stat
```
Expected: only the spec file is staged.

- [ ] **Step 3: Commit the spec**

Run:
```bash
git commit -m "docs: add campfire rename design spec

Locks in the repo and display identity rename to campfire while preserving
plugins/Extras/ storage and all technical IDs."
```
Expected: commit succeeds; `git log --oneline -1` shows the spec commit.

- [ ] **Step 4: Verify the working tree is still clean**

Run: `git status --short`
Expected: no output. The stash remains in `git stash list`.

---

### Task 2: Write the fresh rename-owned `README.md`

**Files:**
- Create: `README.md`

The user's prior untracked README is safely in `stash@{0}`. Write a fresh file from the approved rename scope; do not restore the stash until after the rename commit.

- [ ] **Step 1: Write `README.md`**

Create exactly this content:

```markdown
# Campfire

[![CI](https://img.shields.io/github/actions/workflow/status/aincraft-org/campfire/github-packages.yml?branch=main&label=CI)](https://github.com/aincraft-org/campfire/actions/workflows/github-packages.yml)
[![License: MIT](https://img.shields.io/github/license/aincraft-org/campfire)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-62be7c)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](build.gradle.kts)
[![Folia](https://img.shields.io/badge/Folia-supported-blueviolet)](src/main/resources/paper-plugin.yml)
[![Maven](https://img.shields.io/badge/Maven-GitHub_Packages-2ea44f)](https://github.com/aincraft-org/campfire/packages)

Campfire is the social & daily-life layer for Paper survival servers: persistent
**parties** and **friendships**, cosmetic **titles**, **custom advancements**
with toast notifications, player **mailboxes** with item attachments, safe
**item trading**, multi-channel **chat** with item links, and **daily rewards**
with login streaks and leaderboards.

Everything is backed by persistent storage (SQLite via HikariCP; JSON for titles
and per-player advancement state; YAML for the advancement catalog) and exposed
as Bukkit-free service interfaces through `extras-api`, so other plugins can
build on the same data instead of reimplementing it.

## Features

- **Parties** — persistent parties that survive restarts; party chat and
  lifecycle events.
- **Friends** — persistent friend lists with join/leave awareness.
- **Social menu** — MapGUI-driven menu tying friends, parties, invites, and a
  player picker together (`/social`).
- **Titles** — cosmetic player titles with a JSON catalog.
- **Custom advancements** — YAML-defined advancement catalog granted via SPI,
  surfaced as vanilla advancement toasts (`/advancements`).
- **Mailboxes** — send, read, and claim mail with item attachments; GUI
  compose and inbox (`/mail`).
- **Trading** — two-sided item trade GUI between online players (`/trade`).
- **Chat channels** — global, local, party, market, and LFG channels, with
  chat-item links (cloned stack hover events), cooldowns, and per-player
  channel preferences (`/chat`).
- **Rewards** — configurable daily objectives (mine, kill, craft, XP,
  playtime, login) drawn from a criterion pool, daily rewards, login streaks,
  and leaderboards (`/rewards`).
- **Events** — in-process event service for subscribing to grants,
  party/friend lifecycle, and more.

## Requirements

- Paper (or Folia) 1.21.x server running Java 25
- [MapGUI](https://github.com/flog99/MapGUI) — required dependency (social menu)

## Installation

1. Build or download `extras-paper-<version>.jar`.
2. Drop it into your server's `plugins/` folder alongside MapGUI.
3. Start the server. State lives under `plugins/Extras/` (the on-disk data
   folder is derived from the Paper plugin `name`; this is independent of the
   GitHub repo name) as SQLite databases (`party.db`, `friends.db`,
   `mailbox/mailbox.db`, chat, rewards) and JSON catalogs (titles,
   advancements).

## Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/party` | | Manage persistent player parties |
| `/friend` | `friends` | Manage persistent friendships |
| `/social` | | Open the MapGUI social menu |
| `/title` | `titles` | Manage cosmetic player titles |
| `/advancements` | `xadvancement` | Grant and list custom advancements |
| `/mail` | | Send, read, and claim mailbox mail |
| `/trade` | | Trade items with another online player |
| `/chat` | `ch`, `c` | Switch chat channels and preferences |
| `/rewards` | `daily`, `leaderboard`, `streak` | Claim daily rewards, view streaks and leaderboards |

Permissions default to sensible values (players get `extras.*.use`, operators
get admin actions like rerolling rewards or granting advancements); the full
list is in [`paper-plugin.yml`](src/main/resources/paper-plugin.yml).

## Configuration

- [`rewards.yml`](src/main/resources/rewards.yml) — criterion pool for daily
  objectives, the daily reward payload, and a command allowlist.
- [`advancements.yml`](src/main/resources/advancements.yml) — custom
  advancement catalog (id, display, criteria, toast icon).

## Using the API

`extras-api` is the Bukkit-free artifact (`dev.mintychochip:extras-api`), published to
GitHub Packages together with the shaded plugin (`extras-paper`). Services are
registered on the Bukkit `ServicesManager` under their interfaces:
`PartyService`, `FriendService`, `TitleService`, `MailService`, `ChatService`,
`AdvancementService`, `DailyRewardService`, `LeaderboardService`,
`LoginStreakService`, `ToastService`, and the events service for events.

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/aincraft-org/campfire")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("dev.mintychochip:extras-api:<version>")
}
```

## Building

```bash
./gradlew build        # runs Checkstyle, PMD, SpotBugs, Spotless, tests; jars in build/libs/
./gradlew runServer    # spins up a Paper 1.21.11 test server with the plugin loaded
```

Releases publish `extras-api` and `extras-paper` to GitHub Packages on pushes
to `main` (see [`.github/workflows/github-packages.yml`](.github/workflows/github-packages.yml));
set `-PreleaseVersion=x.y.z` to override the `0.0.0-local` default.

## License

[MIT](LICENSE) © jlo
```

- [ ] **Step 2: Verify both README GitHub Packages surfaces**

Run:
```bash
grep -n 'aincraft-org/campfire' README.md
grep -n 'aincraft-org/extras\|modular-extras' README.md
```
Expected:
- First command shows the CI badge, License badge, **GitHub Packages badge target** (`https://github.com/aincraft-org/campfire/packages`), and Maven endpoint (`https://maven.pkg.github.com/aincraft-org/campfire`).
- Second command produces no output.

- [ ] **Step 3: Confirm the README is the only untracked file**

Run: `git status --short`
Expected: `?? README.md` only.

---

### Task 3: Apply the remaining rename edits

**Files:**
- Modify: `src/main/resources/paper-plugin.yml` — `website` only
- Modify: `settings.gradle.kts` — `rootProject.name`
- Modify: `build.gradle.kts` — POM/scm/publishing URLs and displayNames
- Modify: `config/pmd/pmd.xml` — ruleset name
- Modify: 8 `docs/living-specs/*.md` files — heading prefix

- [ ] **Step 1: Update `paper-plugin.yml` website, preserving `name: Extras`**

Change:
```yaml
website: https://github.com/aincraft-org/modular-extras
```
to:
```yaml
website: https://github.com/aincraft-org/campfire
```
Do not add or change `name: Extras`; this preserves `plugins/Extras/`.

- [ ] **Step 2: Update `settings.gradle.kts`**

Change `rootProject.name = "extras"` to `rootProject.name = "campfire"`.

- [ ] **Step 3: Update `build.gradle.kts` URLs**

Change the POM `url`, all three scm URLs, and the publishing Maven URL:
- `https://github.com/aincraft-org/modular-extras` → `https://github.com/aincraft-org/campfire`
- `https://github.com/aincraft-org/modular-extras.git` → `https://github.com/aincraft-org/campfire.git`
- `ssh://git@github.com/aincraft-org/modular-extras.git` → `ssh://git@github.com/aincraft-org/campfire.git`
- `https://maven.pkg.github.com/aincraft-org/extras` → `https://maven.pkg.github.com/aincraft-org/campfire`

- [ ] **Step 4: Update only the publication displayNames**

Change:
- `displayName = "Extras API"` → `displayName = "Campfire API"`
- `displayName = "Extras Paper"` → `displayName = "Campfire Paper"`

Artifact IDs and publication names stay unchanged: `extras-api`, `extras-paper`, `extrasApi`, `extrasPaper`.

- [ ] **Step 5: Update the PMD ruleset display name**

Change `<ruleset name="Extras PMD Rules"` → `<ruleset name="Campfire PMD Rules"`.

- [ ] **Step 6: Update the 8 living-spec headings**

In `docs/living-specs/{advancements,cinematics,events,friends,parties,rewards,titles,toast}.md`, change each heading's `(modular-extras)` to `(campfire)`. Do not edit historical `docs/superpowers/plans/` or prior `docs/superpowers/specs/`.

- [ ] **Step 7: Verify the rename diff before staging**

Run:
```bash
git diff --no-color --stat
git diff --no-color | grep -E '^\+' | grep -E 'modular-extras|ModularExtras|aincraft-org/extras'
```
Expected: stat contains only the fresh README plus the listed rename files; the second command produces no output.

---

### Task 4: Commit the rename implementation separately

- [ ] **Step 1: Stage only rename-owned paths**

Run:
```bash
git add README.md \
        src/main/resources/paper-plugin.yml \
        settings.gradle.kts \
        build.gradle.kts \
        config/pmd/pmd.xml \
        docs/living-specs/
git diff --cached --stat
git status --short
```
Expected: only rename-owned paths staged; no user WIP exists in the clean tree because it is in the stash.

- [ ] **Step 2: Verify staged technical IDs and storage invariants**

Run:
```bash
git diff --cached --no-color | grep -E '^[-+]((name:|rootProject.name|.*artifactId|.*ExtrasPlugin)|.*extras-api|.*extras-paper|.*extras\.)' || true
grep -n '^name:\|^website:' src/main/resources/paper-plugin.yml
grep -n 'artifactId\|rootProject.name\|maven.pkg.github.com/aincraft-org' build.gradle.kts settings.gradle.kts
```
Expected:
- `paper-plugin.yml` still says `name: Extras` and main remains `dev.mintychochip.ExtrasPlugin`.
- Artifact IDs remain `extras-api`/`extras-paper`.
- `rootProject.name` is `campfire`.
- Maven publishing URL uses `aincraft-org/campfire`.

- [ ] **Step 3: Run the build before committing**

Run: `./gradlew clean check build`
Expected: `BUILD SUCCESSFUL`; tests, Checkstyle, PMD, SpotBugs, Spotless, and shaded build pass.

- [ ] **Step 4: Generate and inspect POMs**

Run:
```bash
./gradlew generatePomFileForExtrasApiPublication generatePomFileForExtrasPaperPublication
grep -E 'campfire|modular-extras|aincraft-org/extras' build/publications/extrasApi/pom-default.xml build/publications/extrasPaper/pom-default.xml
```
Expected: generated POMs contain `github.com/aincraft-org/campfire`, contain no `modular-extras` or `aincraft-org/extras`, and retain artifact IDs `extras-api`/`extras-paper`.

- [ ] **Step 5: Commit only the rename implementation**

Run:
```bash
git commit -m "rename: rebrand repo + display identity to campfire

Implements docs/superpowers/specs/2026-08-25-campfire-rename-design.md.

Technical IDs and plugins/Extras/ storage remain unchanged."
```
Expected: commit succeeds; `git log --oneline -2` shows the spec commit and rename commit. The user's WIP remains safely in the stash.

---

### Task 5: Rename the remote and update local URL

- [ ] **Step 1: Rename the GitHub repository**

Run: `gh api -X PATCH repos/aincraft-org/extras -f name=campfire`
Expected: JSON response includes `"name": "campfire"` and `"full_name": "aincraft-org/campfire"`.

If `gh` lacks repo-admin scope, use GitHub Settings → General → Rename.

- [ ] **Step 2: Update local remote**

Run: `git remote set-url origin https://github.com/aincraft-org/campfire.git`

- [ ] **Step 3: Verify local remote**

Run: `git remote -v`
Expected fetch and push URLs are both `https://github.com/aincraft-org/campfire.git`.

- [ ] **Step 4: Verify old web URL redirects**

Run: `curl -sI -L https://github.com/aincraft-org/extras | head -20`
Expected final response is `200` at the new repo URL.

- [ ] **Step 5: Push the two rename commits**

Run: `git push origin main`
Expected: push succeeds against `aincraft-org/campfire`.

---

### Task 6: Extract the user README draft and restore tracked WIP safely

- [ ] **Step 1: Extract the stashed untracked README to a distinct path**

Do **not** run `git stash pop`: the rename commit now tracks `README.md`, so popping the untracked README onto that path would conflict.

Run:
```bash
mkdir -p .ambient
 git show stash@{0}:README.md > .ambient/README.md.userdraft
```
Expected: `.ambient/README.md.userdraft` exists and contains the original user draft. Compare explicitly:

```bash
grep -n '^# ' .ambient/README.md.userdraft
wc -l .ambient/README.md.userdraft
```
Expected: original draft starts with `# Extras` and has the pre-task line count (120 lines).

- [ ] **Step 2: Restore only the tracked WIP hunks without overwriting tracked rename files**

Because `git stash pop` would also try to restore the untracked README, restore tracked paths from the stash's worktree parent only:

```bash
git diff stash@{0}^1 stash@{0} -- build.gradle.kts src/main/resources/paper-plugin.yml | git apply
```

Expected: working tree now has only the user's two `artifactDescription` changes in `build.gradle.kts` and the user's original `description:` change in `paper-plugin.yml`. The committed rename URLs/displayNames remain unchanged. `README.md` remains the tracked Campfire README.

If `git apply` reports a conflict, do not force it. Save the patch and apply the individual WIP hunks manually after reading the current files; preserve both the committed rename values and the user's WIP values.

- [ ] **Step 3: Verify the committed rename independently**

Run:
```bash
git status --short
git show HEAD:README.md | grep -n 'aincraft-org/campfire'
git show HEAD:README.md | grep -n 'aincraft-org/extras\|modular-extras' || true
git show HEAD:src/main/resources/paper-plugin.yml | grep -n '^name:\|^website:'
```
Expected:
- `HEAD:README.md` is the committed Campfire README, with the Packages badge and Maven endpoint both using `aincraft-org/campfire`.
- `HEAD:paper-plugin.yml` says `name: Extras` and has the Campfire website.
- No stale old repo URLs remain in committed rename-owned files.

- [ ] **Step 4: Verify the user's WIP remains uncommitted and the draft is retained separately**

Run:
```bash
git diff --no-color -- build.gradle.kts src/main/resources/paper-plugin.yml
git status --short
ls -l .ambient/README.md.userdraft
```
Expected:
- The diff contains exactly the user's two publication description changes and original plugin description change.
- The tracked `README.md` is clean; `.ambient/README.md.userdraft` is the separate user draft.
- No user WIP is in `HEAD`.

- [ ] **Step 5: Preserve rollback copies until user review**

Do not drop `stash@{0}` or delete `.ambient/README.md.userdraft` in this task. The user can compare/merge the draft later:

```bash
diff -u .ambient/README.md.userdraft README.md | less
```

The stash remains an additional rollback copy; the extracted draft is the conflict-free review copy.

---

## Self-Review

**1. Spec coverage:**
- Repo rename on GitHub → Task 5 step 1. ✓
- Local remote URL update → Task 5 step 2. ✓
- README H1, description, CI/License/Packages badges, and Maven endpoint → Task 2. ✓
- `paper-plugin.yml` `website` only; `name: Extras` preserved → Task 3 step 1 and Task 4 step 2. ✓
- `settings.gradle.kts` root name → Task 3 step 2. ✓
- `build.gradle.kts` POM/scm/publishing URLs and displayNames → Task 3 steps 3–4. ✓
- PMD display name → Task 3 step 5. ✓
- 8 living-spec headings → Task 3 step 6. ✓
- Storage preservation → Global Constraints and Task 4 step 2. ✓
- Separate spec and implementation commits → Tasks 1 and 4. ✓
- User WIP never committed; reversible stash and tracked-only restoration → Tasks 0 and 6. ✓
- Untracked README never popped onto tracked `README.md`; extracted separately → Task 6 step 1. ✓
- README URL comparisons are explicit, not `git diff` on an untracked file → Tasks 2 and 6. ✓
- Build and generated-POM verification → Task 4 steps 3–4. ✓
- GitHub redirect and push verification → Task 5 steps 3–5. ✓

**2. Placeholder scan:** No `TBD`, `TODO`, `fill in`, or invented user authorization/message. ✓

**3. Consistency:**
- `campfire` slug consistent across repo URLs, badges, Maven endpoint, POM, settings, and living specs. ✓
- `Campfire` display name consistent. ✓
- `plugins/Extras/`, `name: Extras`, and all technical IDs unchanged. ✓
- README Packages badge and Maven endpoint separately and explicitly covered. ✓
- Restored user WIP remains uncommitted; extracted README remains separate. ✓
