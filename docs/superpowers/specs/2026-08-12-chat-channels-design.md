# Chat Channels and Item Links Design

## Goal

Add persistent player chat channels and Adventure-based chat formatting to the Paper plugin. The first release provides Global, Local, Party, Market, and LFG channels plus `%item` links for the item held in the sender's main hand.

## Selected Scope

- Channels: Global, Local, Party, Market, and LFG.
- Persist active and muted channel preferences in SQLite.
- `%item` produces a native item hover link.
- No price or LLM estimates in the first release.
- Price enrichment remains a future optional integration.
- No claim of true sent-message editing; Paper exposes deletion/replacement rather than in-place editing.

## User Experience

`/chat` is the canonical command with aliases `/ch` and `/c`.

- `/chat <global|local|party|market|lfg>` changes the persistent active channel.
- `/chat send <channel> <message>` sends once without changing the active channel.
- `/chat mute <channel>` and `/chat unmute <channel>` persist notification preferences.
- `/chat status` reports the active and muted channels.
- `/chat channels` and `/chat help` list available commands.

Normal chat routes through the selected channel. Unknown players default to Global with no muted channels. Selecting a channel automatically unmutes it. Global cannot be muted, guaranteeing a valid fallback.

Defaults:

- Local radius: 100 blocks, inclusive, same world only.
- Global cooldown: 2 seconds.
- Local and Party cooldown: 1 second.
- Market and LFG cooldown: 5 seconds.
- Maximum trimmed message length: 256 Unicode code points.
- At most one `%item` token per message.

`%item` is case-insensitive. It becomes `[Item Name xN]` with the native hover payload from a cloned main-hand `ItemStack`. Empty hand rejects the message with a clear instruction. `%%item` emits literal `%item` text.

## Chat Formats

- Global: gray `[G] <title?> <name>: <message>`
- Local: yellow `[L] <title?> <name>: <message>`
- Party: aqua `[P] <title?> <name>: <message>`
- Market: gold `[MARKET] <title?> <name>: <message>`
- LFG: green `[LFG] <title?> <name>: <message>`

Formats use Adventure components. Equipped titles come from `TitleService`. Channel decoration is server-authored and does not alter the signed original content.

## Architecture

### Bukkit-free service

Add `ChannelId`, immutable `ChannelPreferences`, `ChatResult`, and `ChatService` under `dev.mintychochip.api`. `DefaultChatService` owns defaults, selected/muted invariants, validation, and persistence. No Bukkit, Adventure, JDBC, world, location, or item types enter the public API.

`SqliteChatRepository` stores preferences in `chat.db`. Absent rows return Global with no muted channels. Failed writes preserve prior state.

### Routing

- Global, Market, and LFG: all online players who have not muted the channel.
- Local: online players in the same world whose squared distance is at most `100 * 100`.
- Party: members from the current immutable `PartyService.partyOf(sender)` snapshot; reject senders without a party.

Recipient selection uses immutable presence snapshots. It does not persist online state, world, location, party membership, names, titles, or items.

### Folia-safe Paper adapter

`ChatPresenceRegistry` stores immutable online snapshots keyed by player UUID. Join, quit, teleport, and world-change events update it from valid event contexts. The asynchronous chat listener reads only these snapshots.

For `%item`, `AsyncChatEvent` obtains a cloned main-hand item through the sender's entity scheduler and a bounded future. The asynchronous event thread never reads mutable player inventory, world, or location state directly. A timeout rejects the link rather than blocking indefinitely.

`ChatListener` validates text and cooldowns, selects recipients from immutable snapshots, filters the event's mutable viewer set, and installs an Adventure renderer. It keeps the original signed message semantic content; the renderer adds channel/title/name decoration and item hover components.

### Item link rendering

`ItemLinkRenderer` is Paper-only. It parses `%item` and `%%item`, constructs the compact label, and attaches `ItemStack.asHoverEvent()` from the cloned stack. It never mutates inventory or sends item data externally.

### Commands and lifecycle

`ChatCommand` follows existing `BasicCommand` conventions. `ExtrasPlugin` constructs the repository/service, registers chat and presence listeners, registers `/chat`, and closes the repository/listeners during disable. `paper-plugin.yml` declares `extras.chat.use` and per-channel permissions, default true.

## Persistence

```sql
CREATE TABLE chat_preferences (
  player_id BLOB PRIMARY KEY,
  active_channel TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE chat_muted_channels (
  player_id BLOB NOT NULL,
  channel TEXT NOT NULL,
  PRIMARY KEY (player_id, channel),
  FOREIGN KEY (player_id) REFERENCES chat_preferences(player_id) ON DELETE CASCADE
);
```

Only known channel keys are accepted.

## Errors and Safety

- Party chat rejects players without a party.
- Local chat rejects when the sender lacks a safe presence snapshot.
- Empty and over-length messages are rejected.
- Empty-hand `%item` rejects the message.
- Cooldown failures report remaining delay.
- SQLite failure preserves prior preferences.
- Missing recipient snapshots exclude those recipients.
- No asynchronous path directly reads Bukkit world, location, or inventory state.
- Market and LFG have longer cooldowns and no inferred pricing.

## Testing

Core tests cover preference defaults, persistence restart, selection/muting, Global fallback, channel parsing, and repository failure behavior. Routing tests cover Global/Market/LFG filtering, Local same-world and inclusive 100-block boundaries, Party membership, muted recipients, and missing presence.

Paper-focused tests cover token parsing, literal escapes, item labels, empty-hand behavior, and channel formatting where headless Paper permits. Live Paper smoke verification exercises all channels, Local range boundaries, Party routing, reconnect persistence, mute behavior, `%item` hover, empty-hand rejection, cooldowns, and shutdown/restart without asynchronous thread-check exceptions.

## Future Price Enrichment

A future optional `PriceEstimateProvider` may use auditable local market history or an explicitly configured external provider. It must be disabled by default, asynchronous, timeout/cost bounded, privacy-safe, and clearly labeled as an estimate. Because sent messages cannot be edited in place, estimates would appear in a later follow-up component or inspect command.
