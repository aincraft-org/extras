# Cinematics (modular-extras) — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

## Intent

Operators author named cinematic scenes: a camera path through world space plus
timed **shader** overlays and timed **props**, then play that scene for a
target player. Vanilla clients must work — overlays are server-triggered
post/core effects (status-effect shaders or pack-backed overlay ids), not
Iris/OptiFine packs.

Success looks like a cutscene: the player's camera interpolates along keyframes
for the scene duration, overlays and props appear only inside their time
windows, and stop/completion restores the player's pre-play pose and clears
those overlays and props.

## Boundaries

### In scope
- Named scenes of camera keyframes (world identity, position, yaw, pitch)
- Timed shader overlay cues and timed prop cues
- Sampling at elapsed time `t` in `[0, duration]`
- Playback sessions (play / stop / natural completion / restore)
- Operator command using the terms **camera**, **shaders**, **props**
- JSON persistence under `<data>/cinematics/`
- `CinematicService` SPI registration for downstream consumers

### Out of scope / non-goals
- Iris / OptiFine / Vibrant Visuals client shader packs
- Replay recording, video export, Flashback/ReplayMod clones
- In-game particle path editor, screen shake, FOV zoom, camera roll,
  upside-down camera, NPC actors, dialogue, timestamped arbitrary commands
- A separate standalone Gradle project / second plugin JAR

## Invariants

- A scene with fewer than two camera keyframes is rejected for load/play.
- Keyframe times are finite, `>= 0`, and strictly increasing.
- `sample(0)` matches the first keyframe; `sample(duration)` matches the last.
- A mid-duration sample lies on the path between the endpoint positions and is
  not a jump to the last keyframe.
- Shader overlays and props are active only while `t` is inside their window
  (inclusive of both endpoints).
- Play records the player's pre-play pose. Stop or natural completion restores
  that pose and reports no remaining overlays/props.
- A second play for the same player is rejected (`ALREADY_PLAYING`) until stop
  or completion.
- Public API types do not mention Bukkit `World` or `Player`.

## Implementation guidance

- `api.cinematic` = Bukkit-free SPI + immutable value types (`CinematicService`,
  `CinematicScene`, `CameraPose`, cues, `SceneSample`, `PlaybackSnapshot`).
- `core` = `DefaultCinematicService` (drafts, play sessions, JSON repository)
  with pose interpolation and window evaluation callable from JUnit without a
  server.
- `paper` = Folia player scheduler applies sampled poses, spawns/removes
  display-entity props, turns overlay ids on/off (vanilla potion-effect shaders
  when the id maps), and registers `/cinematic`.
- Files: `<data>/cinematics/<name>.json`; missing files decode to no scene;
  corrupted values degrade rather than crash enable.
- Command: `/cinematic` (aliases `cinematics`, `cine`), permission
  `extras.cinematic.use` (default op), registered via lifecycle events **and**
  declared in `paper-plugin.yml`.
- Tests drive `CinematicScene.sample` / `CinematicService` play-stop — not a
  re-implementation of interpolation. Assert endpoint / between / window
  properties, not a hardcoded lerp formula.

## Current

- [x] Named scene model with keyframes, shader cues, prop cues, duration
- [x] Load rejects fewer than two keyframes
- [x] Time sampling: interpolated camera + active overlay/prop sets
- [x] Play/stop session restores pre-play pose and clears cues
- [x] Paper adapter + `/cinematic` camera / shaders / props / play / stop
- [x] JUnit coverage and plugin descriptor permissions

## Next

- [ ] Per-viewer display props (hide from other players by default)
- [ ] Optional spectator freeze while a scene is playing

## Future

- [ ] Bezier / Catmull-Rom camera paths
- [ ] Screen shake, FOV zoom, camera roll
- [ ] NPC actors and timestamped command events

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-19 | Bukkit-free scene sampling and playback sessions; Paper only applies | Tests must run without a connected client |
| 2026-08-19 | Shader overlays are vanilla post/core effect ids, not client packs | Vanilla clients must work |
| 2026-08-19 | Second play for a player is rejected, not stacked | Restores stay unambiguous; operator stops first |
| 2026-08-19 | Duration is the last keyframe time; cues outside that window never play | `sample(duration)` matching the last keyframe stays well-defined |

## Open questions

- [x] Inclusive cue windows? Yes: `start <= t <= end`.
