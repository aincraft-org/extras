# Rename `aincraft-org/extras` to `aincraft-org/campfire`

> Status: draft
> Date: 2026-08-25
> Owner: jlo

## Goal

The repo is the social & daily-life layer for Paper survival servers (parties, friends, mail, trade, chat channels, daily rewards, titles, custom advancements). The current name `extras` undersells that — it reads as a junk-drawer of leftovers rather than a coherent social platform.

Rename the GitHub repo (and the user-facing display identity) to `campfire`. Preserve runtime/storage identity so existing dev servers and their SQLite data keep working.

## Non-goals

- Renaming the Gradle artifact IDs (`extras-api`, `extras-paper`) — they stay.
- Renaming Java packages (`dev.mintychochip.api`, `dev.mintychochip.*`) — they stay.
- Renaming permission nodes (`extras.*`) — they stay.
- Renaming command names (`/party`, `/friend`, `/mail`, `/social`, etc.) — they stay.
- Renaming the Paper plugin `name` (which controls the on-disk data folder) — it stays `Extras`.
- Editing historical plans/specs in `docs/superpowers/plans/` and `docs/superpowers/specs/` — those are records of past work; frozen as-is. (A note at the top of each spec file acknowledging the rename is fine; rewriting the body is not.)
- Migrating data from `plugins/Extras/` to `plugins/Campfire/` — not done. See "Storage decision" below.

## Why "campfire"

The plugin's gravity well is social (parties, friends, mail, trade), anchored by daily-life rituals (login streaks, daily rewards, chat channels). "Campfire" names the gathering place: where the community meets, where people return every day. It fits the `aincraft` org (survival/craft vocabulary), reads as a natural subcommand tree (`/campfire social`, `/campfire mail`, `/campfire rewards`), and is short and memorable. Verified `github.com/aincraft-org/campfire` returns 404 — the slug is free.

## Storage decision (the important constraint)

Paper derives a plugin's data folder from the `name` field in `paper-plugin.yml`. The Java code uses `getDataFolder()` exclusively — no hardcoded `"Extras"` paths.

To preserve existing dev-server SQLite data:

- `paper-plugin.yml` `name: Extras` **stays**.
- On-disk folder remains `plugins/Extras/`.
- The README is updated to describe the on-disk path as `plugins/Extras/` (the actual runtime path), not the new display name.

The brand mismatch is intentional and bounded: admins see `Extras` in their running plugins list and `plugins/Extras/` on disk, but every external surface (GitHub, README, Maven, workflow) reads "Campfire."

If the user later wants to migrate the on-disk folder to `plugins/Campfire/`, that is a separate change requiring an explicit data-migration step and is out of scope here.

## Changes

### Display & config

| File | Change |
|---|---|
| `README.md` | H1 `# Extras` → `# Campfire`; description blurb rewritten; badge URLs repointed from `aincraft-org/extras` → `aincraft-org/campfire`; Maven coordinate URL updated; "State lives under `plugins/Extras/`" line clarified as runtime behavior (this is the existing on-disk path) |
| `paper-plugin.yml` | `name: Extras` **stays** (storage constraint); `website: https://github.com/aincraft-org/modular-extras` → `https://github.com/aincraft-org/campfire` |
| `settings.gradle.kts` | `rootProject.name = "extras"` → `rootProject.name = "campfire"` |
| `build.gradle.kts` | POM `url` (`modular-extras` → `campfire`); scm `connection`, `developerConnection`, `url` (`modular-extras` → `campfire`); `publishing.repositories.maven.url` (`.../aincraft-org/extras` → `.../aincraft-org/campfire`); publication displayName strings (`Extras API` → `Campfire API`, `Extras Paper` → `Campfire Paper`); publication artifact descriptions stay accurate (already describe features, not the name); publication IDs `extrasApi`/`extrasPaper` and artifactIds `extras-api`/`extras-paper` **stay** |
| `config/pmd/pmd.xml` | `<ruleset name="Extras PMD Rules">` → `<ruleset name="Campfire PMD Rules">` |
| `.github/workflows/github-packages.yml` | No hardcoded repo name to update; the workflow uses `${{ github.repository }}` context. No change required. Verify after edit. |

### Forward-looking docs

| File | Change |
|---|---|
| `docs/living-specs/*.md` (8 files) | Heading `(modular-extras)` → `(campfire)` |

### Historical docs (frozen)

- `docs/superpowers/plans/*` and `docs/superpowers/specs/*` — left as-is. These are records of past work. (No edit.)
- The historical `dev.jlo.extras` package references in old plans are an artifact of an even earlier migration; out of scope.

### Remote & local

| Step | Command |
|---|---|
| Rename on GitHub | `gh api -X PATCH repos/aincraft-org/extras -f name=campfire` (or via web UI) |
| Update local remote URL | `git remote set-url origin https://github.com/aincraft-org/campfire.git` |
| Verify | `git remote -v` shows new URL; `git push` works against the new URL; old `https://github.com/aincraft-org/extras` URL returns a GitHub redirect to the new repo |

## Stays unchanged (explicit)

- Gradle artifact IDs: `extras-api`, `extras-paper`
- Java packages: `dev.mintychochip.api`, `dev.mintychochip.*`
- Permission nodes: `extras.*`
- Commands: `/party`, `/friend`, `/mail`, `/trade`, `/chat`, `/rewards`, `/social`, `/title`, `/advancements`
- Paper plugin `name`: `Extras` (and therefore the on-disk data folder `plugins/Extras/`)
- `paper-plugin.yml` `main` class: `dev.mintychochip.ExtrasPlugin`
- Workflow publication targets: the artifacts are published from `${{ github.repository }}` which auto-updates after the rename; the artifact filenames on the release (`extras-paper-*.jar`, `extras-api-*.jar`) stay the same since those are derived from `archiveBaseName` in `build.gradle.kts`.

## Risks & verification

- **GitHub web redirect:** GitHub automatically redirects `github.com/aincraft-org/extras` to the new repo name. No action required; verify after rename.
- **Maven coordinate URL:** `maven.pkg.github.com/aincraft-org/extras` is the GitHub Packages endpoint and is *not* a redirected URL — it is a different surface. The old URL will be dead. The user has confirmed no published consumers depend on it, so this is acceptable. (The new URL `maven.pkg.github.com/aincraft-org/campfire` works because GitHub Packages uses the renamed repo's namespace.)
- **On-disk data:** `plugins/Extras/` is preserved by keeping the plugin `name`. Existing dev servers keep their SQLite data unchanged.
- **CI:** the workflow references `${{ github.repository }}` and `${{ steps.version.outputs.version }}` — no hardcoded repo name. After the rename and `git remote set-url`, push should work without further config changes.
- **Stale references:** after edits, re-grep the repo for `extras`/`Extras` in display contexts. Remaining hits should be limited to: technical IDs (artifact names, package roots, permission nodes, command names), the plugin `name` field, the `plugins/Extras/` runtime path documentation, and historical plan/spec files.

## Acceptance criteria

1. `git remote -v` shows `https://github.com/aincraft-org/campfire.git`.
2. `github.com/aincraft-org/extras` returns a redirect to `github.com/aincraft-org/campfire`.
3. `README.md` H1 is `# Campfire`; all badge URLs and the Maven coordinate URL point to `aincraft-org/campfire`.
4. `paper-plugin.yml` `name` is still `Extras`; `website` is `https://github.com/aincraft-org/campfire`.
5. `settings.gradle.kts` has `rootProject.name = "campfire"`.
6. `build.gradle.kts` POM `url`/scm fields point to `aincraft-org/campfire`; publication displayNames are `Campfire API` and `Campfire Paper`; artifactIds remain `extras-api` and `extras-paper`.
7. All `docs/living-specs/*.md` headings reference `(campfire)`.
8. `./gradlew clean check build` passes.
9. `grep -r 'modular-extras\|ModularExtras' docs/living-specs/ README.md build.gradle.kts settings.gradle.kts paper-plugin.yml config/pmd/pmd.xml` returns no matches.
10. `git push` to the renamed remote succeeds.

## Out of scope (deferred)

- Migrating `plugins/Extras/` to `plugins/Campfire/` on disk.
- Renaming Java package roots from `dev.mintychochip.*` to `dev.campfire.*` or similar.
- Renaming Gradle artifact IDs.
- Renaming permission nodes.
- Renaming command names.
- Rewriting historical plan/spec files.
