# MapGUI Social Menu Design

## Goal

Add a MapGUI-based graphical social menu to the Extras Paper plugin. `/friend list`,
`/party list`, and a new `/social` command open a tabbed map GUI that lets players
view friends, manage a party, and handle pending friend requests and party invites.
A player picker ("party picker") lets a party leader invite an online player, and a
friend request can also be sent from the same picker.

## Constraints

- MapGUI 1.1.0 is compiled for **Java 25**, so the project must bump its Java toolchain
  from 21 to 25. Production servers also need Java 25 to load the MapGUI plugin.
- `io.github.flog99:mapgui-api:1.1.0` must stay **compile-only** and **must not be
  shaded or relocated**. Shading would place a second copy of `de.flog99.mapgui.*`
  on the classpath and break `join-classpath` with the MapGUI runtime.
- The MapGUI Paper plugin must be installed on the server as a separate jar.
- The existing Extras project uses the Gradle Shadow plugin for `sqlite-jdbc`; no
  MapGUI classes go into the shadow jar.

## Scope

In scope:

- New `/social` command and `/friend list` / `/party list` opening the same
  MapGUI `SocialScreen` on the matching tab.
- Three tabs: **Friends**, **Party**, and **Invites**.
- Friends list with online/offline presence, remove friend, and pending friend
  requests (accept/decline/cancel).
- Party view with members, leader actions (kick, transfer, disband, invite), and
  member leave. A "Create party" flow for players with no party.
- Player picker for inviting a player to a party or adding them as a friend.
- `compileOnly` Gradle dependency on `mapgui-api`, plugin dependency in
  `paper-plugin.yml`, and Java 25 toolchain bump.

Out of scope:

- Custom themes, fonts, images, or animations beyond the MapGUI defaults.
- Server-wide party discovery (`/party list` for non-members).
- Friend chat, party chat, teleport, nicknames, or favorite tiers.
- Persistent GUI state across reconnects.

## Design decisions

### Dependency model

`build.gradle.kts` adds:

```kotlin
dependencies {
    compileOnly(libs.mapgui.api)
    // ... existing
}
```

`gradle/libs.versions.toml` adds:

```toml
[libraries]
mapgui-api = { module = "io.github.flog99:mapgui-api", version = "1.1.0" }
```

`paper-plugin.yml` declares MapGUI as a required server dependency so Extras can
access `MapGui.get()` at runtime:

```yaml
dependencies:
  server:
    MapGUI:
      load: BEFORE
      required: true
      join-classpath: true
```

### Java toolchain

MapGUI 1.1.0 class files are version 69 (Java 25). The project must compile with
Java 25 to resolve the API and run against it:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

- `SocialScreen` extends `de.flog99.mapgui.Screen` and is passed `FriendService`
  and `PartyService` and an initial `SocialTab`.
- A single screen owns the whole social menu. Tabs switch with a `State<SocialTab>`.
  The player picker is rendered in the same screen via `State<PlayerPickerMode>`,
  with a `Back` button returning to the tab stored in `State<SocialTab> previousTab`.
- When the picker opens, `previousTab` is set to the current tab and `activeTab`
  is set to a special `PICKER` tab; `Back` restores `previousTab`.
- `build()` uses the `de.flog99.mapgui.ui.Ui.*` DSL and `Column`/`Row` lists built
  with `each(...)` and `Scroll` when content exceeds the 128x128 canvas.
- Data is loaded into local fields in `onOpen()` and refreshed after every mutation,
  then `invalidate()` is called to rebuild.

### Tab layout

A fixed top bar holds three tab buttons. The active tab gets an accent background.
The body below is the tab content.

#### Friends tab

- Header: `Add Friend` button.
- Friend rows: name colored by online status (green online, gray offline), with a
  `Remove` button on the right.
- Pending requests section:
  - Incoming: requester name with `Accept` / `Decline`.
  - Outgoing: target name with `Cancel`.
- `Add Friend` switches to the player picker in `ADD_FRIEND` mode.

#### Party tab

- If in a party:
  - Header: party name (or "Unnamed party"), `Invite`, `Disband` (leader only), and
    `Leave`.
  - Member rows: name, leader marker, and leader-only `Kick` / `Transfer` buttons.
    The viewer's own row shows `Leave` instead.
  - `Invite` opens the player picker in `INVITE_TO_PARTY` mode.
- If not in a party:
  - `Create Party` with an optional `Field` for the party name and a `Create` button.
  - Pending party invites with `Accept` / `Decline`.

#### Invites tab

- Combined view of pending friend requests and party invites with the same accept,
  decline, and cancel buttons. This keeps the Friends and Party tabs focused on
  membership rather than pending traffic.

### Player picker

A scrollable list of online players filtered by context:

- `ADD_FRIEND`: exclude self, existing friends, and players with a pending request
  in either direction.
- `INVITE_TO_PARTY`: exclude self, players already in a party, and players already
  invited to this party.

Each row shows the player name and an `Add` or `Invite` button. A `Back` button
returns to the tab that opened the picker.

### Entry points

- `/social` opens the menu. If the player is in a party, it starts on the **Party**
  tab; otherwise it starts on **Friends**.
- `/friend list` opens on the **Friends** tab.
- `/party list` opens on the **Party** tab.
- Console/non-player callers keep the existing text output.

### Commands and permissions

- New `SocialCommand` registered in `ExtrasPlugin` during `LifecycleEvents.COMMANDS`.
- New `extras.social.use` permission, default `true`.
- `FriendCommand.list()` and `PartyCommand.list()` are updated to open the GUI for
  players; if MapGUI is not available they fall back to the existing text output.

### Feedback and error handling

- Service results (`ALREADY_FRIENDS`, `PARTY_FULL`, `NOT_LEADER`, etc.) are sent as
  action bar text so they do not break the map view.
- Failed mutations do not close the GUI; `refresh()` is only called on success.
- If `MapGui.get()` is null, the commands fall back to the old text behavior.

### Folia note

MapGUI does not advertise Folia support. Extras currently declares
`folia-supported: true`. This design does not remove that flag, but using MapGUI on
a Folia server is a runtime risk that should be tested.

## Testing

- Unit tests for pure filtering helpers (e.g., which players appear in the picker
  for a given mode).
- `./gradlew runServer` smoke test: open `/friend list`, `/party list`, and
  `/social`; switch tabs, accept/decline a request, invite a player, create a party,
  and leave/disband.
- Build verification: `./gradlew build` passes with `mapgui-api` as compile-only,
  Java 25 toolchain, and the updated `paper-plugin.yml`.
