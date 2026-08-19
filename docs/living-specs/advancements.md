# Advancements (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

## Intent

Operators and downstream plugins define custom advancements (stable id +
Minecraft display fields). Granting one persists completion and, on the first
successful grant, shows a Minecraft advancement toast via the standalone
`ToastService` SPI — not chat, title, or action bar.

## Boundaries

### In scope
- Catalog of custom advancements: id, title, description, icon (material key),
  frame (`task` / `goal` / `challenge`)
- Grant / has / list completions with JSON-per-player persistence
- Idempotent repeat grant; unknown ids rejected and not persisted
- First-time grant publishes `AdvancementGranted` after persist, then the
  paper listener calls `ToastService` with title, icon, and frame
- Operator `/advancements grant|list` and ServicesManager SPI registration

### Out of scope / non-goals
- Vanilla advancement-screen trees, tabs, parent/child links, datapack import
- Automatic gameplay criteria (block-break, kills) — rewards domain
- Chat announcements, XP/loot, recipe unlocks, MapGUI browser
- Cross-server completion sync

## Invariants

- Catalog ids are trimmed, lowercased, 1–64 non-control non-whitespace chars.
- Grant of an id absent from the catalog is `UNKNOWN_ADVANCEMENT` and writes
  nothing.
- First successful grant persists the id, is readable via `has`/`completed`,
  and emits exactly one `AdvancementGranted`.
- Repeat grant of a completed id is `ALREADY_COMPLETED`, does not duplicate
  the completion, emits no event, and requests no toast.
- Completions survive reconstructing the service on the same store.
- Toast payload title, icon, and frame match the catalog entry and are sent
  through `ToastService` (not a parallel advancement-owned sender).
  Chat/title/action-bar are not substitutes.

## Implementation guidance

- `api` = Bukkit-free SPI (`AdvancementService`, `CustomAdvancement`,
  `AdvancementFrame`, `AdvancementResult`). Toasts live in `api.toast`.
- `core` = `DefaultAdvancementService` (single mutation lock, per-player
  cache) over `JsonAdvancementRepository` (`<data>/advancements/<uuid>.json`).
  Events publish only after the JSON write, outside the mutation lock.
- `paper` = YAML catalog (`advancements.yml`), `AdvancementCommand`, and
  `AdvancementToastListener` on `AdvancementGranted` which maps catalog
  display fields onto `ToastService`. Packet sending is owned by toast.
- Register `AdvancementService` on ServicesManager at Normal priority.
  Toast registration is the toast domain's job.

## Current

- [x] Custom-advancement catalog + grant/query/persist SPI
- [x] First-time grant toast via standalone `ToastService`
- [x] `/advancements` grant/list + permissions + plugin registration

## Next

- [ ] Optional revoke / un-complete
- [ ] Declare more sample catalog entries tied to existing domains

## Future

- [ ] Automatic grants from rewards/party milestones
- [ ] Advancement browser in MapGUI

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-19 | JSON-per-player completions, in-memory+YAML catalog | Completions are a set of ids (titles-shaped); catalog is operator/downstream defined |
| 2026-08-19 | Toast via temporary advancement display packet, not chat/title/action bar | Paper 1.21.11 has no `Player.sendToast`; criterion is AdvancementDisplay + `show_toast` |
| 2026-08-19 | Advancements consume `ToastService` instead of owning a sender | Toast is a shared SPI; Adventure has no sendToast |
| 2026-08-19 | Command is `/advancements` (not vanilla `/advancement`) | Avoid colliding with Minecraft's advancement command |

## Open questions

- [ ] Should offline grants queue a toast for next join? (currently: skip if offline)
