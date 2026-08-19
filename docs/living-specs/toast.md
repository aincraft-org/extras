# Toast (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

## Intent

A Bukkit-free toast SPI so any Extras domain (or downstream plugin) can show a
Minecraft advancement toast: title, icon, and frame. Adventure has no
`sendToast`; Paper 1.21.11 has no `Player.sendToast`. This domain owns the
packet path so advancements are a consumer, not the owner.

## Boundaries

### In scope
- `ToastService` / `ToastRequest` / `ToastFrame` (`task`, `goal`, `challenge`)
- Paper adapter: `AdvancementDisplay` frames + temporary `show_toast`
  grant/revoke
- ServicesManager registration for downstream senders

### Out of scope / non-goals
- Chat, title, action bar, or boss-bar stand-ins
- Vanilla advancement trees, persistence, or catalogs (advancements domain)
- Queuing toasts for offline players (currently skip if offline)

## Invariants

- Payload is player id, non-blank title, material-key icon, and a frame.
- Paper mapping uses `io.papermc.paper.advancement.AdvancementDisplay.Frame`
  and `show_toast: true`. `announce_to_chat` is false.
- Offline or unknown players are skipped; send is best-effort display.

## Implementation guidance

- `api.toast` = Bukkit-free SPI. Do not import advancement catalog types.
- `paper` = `PaperToastSender` maps `ToastRequest` → `PaperToastDisplay` and
  sends via `UnsafeValues.loadAdvancement` + award/revoke. Tests drive the
  shipped mapper (`PaperToastDisplay.from`) with a recording transport.
- Register `ToastService` at Normal priority. Advancements subscribe to
  `AdvancementGranted` and call `ToastService.send`; they must not own a
  parallel toast sender.

## Current

- [x] `ToastService` SPI + Paper `show_toast` adapter
- [x] Registered on ServicesManager
- [x] Advancements consume `ToastService` on first-time grant

## Next

- [ ] Optional operator `/toast` for live checks
- [ ] Queue toasts for offline players until join

## Future

- [ ] Adventure `Audience` toast if Kyori/Paper ever add one — replace the
      packet adapter, keep this SPI

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-19 | Toast is its own SPI, not part of advancements | Adventure has no toast API; multiple domains should reuse one sender |
| 2026-08-19 | Packet path is temporary advancement display, not chat/title | Only path that produces the vanilla framed toast on Paper 1.21.11 |

## Open questions

- [ ] Should `ToastRequest` carry an optional description for the hidden advancement JSON?
