# Extras

[![CI](https://img.shields.io/github/actions/workflow/status/aincraft-org/extras/github-packages.yml?branch=main&label=CI)](https://github.com/aincraft-org/extras/actions/workflows/github-packages.yml)
[![License: MIT](https://img.shields.io/github/license/aincraft-org/extras)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-62be7c)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](build.gradle.kts)
[![Folia](https://img.shields.io/badge/Folia-supported-blueviolet)](src/main/resources/paper-plugin.yml)
[![Maven](https://img.shields.io/badge/Maven-GitHub_Packages-2ea44f)](https://github.com/aincraft-org/extras/packages)

Extras is a standalone Paper plugin that fills in the social and quality-of-life
layer for survival servers: persistent **parties** and **friendships**, cosmetic
**titles**, **custom advancements** with toast notifications, player
**mailboxes** with item attachments, safe **item trading**, multi-channel
**chat** with item links, and **daily rewards** with login streaks and
leaderboards.

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
- **Events** — in-process `ExtrasEventService` for subscribing to grants,
  party/friend lifecycle, and more.

## Requirements

- Paper (or Folia) 1.21.x server running Java 25
- [MapGUI](https://github.com/flog99/MapGUI) — required dependency (social menu)

## Installation

1. Build or download `extras-paper-<version>.jar`.
2. Drop it into your server's `plugins/` folder alongside MapGUI.
3. Start the server. State lives under `plugins/Extras/` as SQLite databases
   (`party.db`, `friends.db`, `mailbox/mailbox.db`, chat, rewards) and JSON
   catalogs (titles, advancements).

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
`LoginStreakService`, `ToastService`, and `ExtrasEventService` for events.

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/aincraft-org/extras")
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
