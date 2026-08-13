# Titles (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-08
> Owners: jlo

## Intent

Cosmetic player titles migrated from the Azoth character host into
ModularExtras. Titles are arbitrary strings with no fixed catalog: a player
holds an unordered set of unlocked titles and may equip at most one at a time.

## Boundaries

### In scope
- Grant/revoke (admin), equip/unequip/list (self), admin list-others
- 1–64 non-control-character title ids, trimmed on grant/equip
- JSON-per-player persistence under `<data>/titles/`
- `TitleService` SPI registration for downstream consumers

### Out of scope / non-goals
- No title catalog / unlocks catalog / rendering (chat prefix, tablist)
- No auto-grant systems (quests, milestones)
- No per-player profile coupling — title state is standalone and created
  lazily on first successful mutation

## Invariants

- At most one equipped title per player; equipped is always a member of the
  unlocked set (decode drops an equipped title absent from unlocked).
- Duplicate grant → `ALREADY_UNLOCKED`; equip/revoke of unowned →
  `NOT_UNLOCKED`; blank/oversized/control ids → `INVALID_TITLE`.
- Revoking the equipped title unequips it.
- Unknown players read as empty and accept grants (lazy creation; no
  `PROFILE_NOT_FOUND` — this plugin has no character profiles).
- All read views are immutable snapshots.
- Every mutation persists immediately; each player is one JSON document.

## Implementation guidance

- `api` = Bukkit-free SPI + immutable `TitleProfile` snapshot
  (`TitleService`, `TitleResult`, `TitleProfile`).
- `core` = `DefaultTitleService` (single mutation lock, per-player cache,
  `MutableTitleProfile`) over a hand-rolled JSON codec
  (`JsonTitleRepository`, mirrors the azoth character-profile codec).
- Files: `<data>/titles/<uuid>.json` with `unlockedTitles` +
  `equippedTitle`; missing/legacy files decode to empty; corrupted values
  degrade, never fail the load.
- `paper` = `TitleCommand` (BasicCommand, `/title`), admin permission
  `extras.titles.admin` checked per-subcommand; self commands require a
  player. Player-id resolution reuses the shared `PlayerIds` helpers.
- Registered in `ExtrasPlugin.onEnable` via `LifecycleEvents.COMMANDS`
  under `title` (alias `titles`); service registered on the ServicesManager.

## Current

- [x] `TitleService` SPI (grant/revoke/equip/unequip/read views)
- [x] `DefaultTitleService` with lazy state + `JsonTitleRepository` codec
- [x] `/title` command (grant/revoke/equip/unequip/list) + admin permission
- [x] Committed-domain events for grant/revoke/equip/unequip (actual changes only)
- [x] Green build: 62 tests, shaded jar

## Next

- [ ] Render equipped title in chat join message or tablist
- [ ] Declare the admin permission in `paper-plugin.yml`

## Future

- [ ] Title catalog with auto-grants
- [ ] Delegated grant (staff) or title packs

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-08 | Migrate titles to standalone `TitleService` with lazy creation, dropping `PROFILE_NOT_FOUND` | This plugin has no character profiles; azoth's profile coupling doesn't apply |
| 2026-08-08 | JSON-per-player files under `titles/` (hand-rolled codec) | Mirrors azoth's proven approach; no third-party dependency |
| 2026-08-08 | Admin permission `extras.titles.admin` (code-checked, not declared in metadata) | Matches azoth's grant/revoke gating; declaration deferred to Next |
| 2026-08-08 | Command is `/title` (alias `titles`), registered via lifecycle events | Folia-compatible; consistent with `/party` and `/friend` |

## Open questions

- [ ] Should titles render anywhere (chat join, tablist) or stay management-only?
