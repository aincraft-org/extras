# Modular Mailbox Migration Design

## Goal

Fold the standalone `modular-mailbox` Paper plugin into the existing
`../modular-extras` plugin without changing mailbox behavior or disturbing the
existing party, friend, and title domains. The source repository remains
untouched; the destination becomes the sole runtime host for the migrated
mailbox implementation.

## Context and constraints

- `modular-mailbox` is a three-project Gradle build (`api`, `common`, `paper`).
- `modular-extras` is a single-module Paper plugin whose packages are organized
  as `dev.jlo.extras.api`, `dev.jlo.extras.core`, and `dev.jlo.extras.paper`.
- `dev.jlo.extras.ExtrasPlugin` is the only destination plugin entrypoint.
- `paper-plugin.yml` must retain only `dev.jlo.extras.ExtrasPlugin` as `main`.
- The destination already provides Paper 1.21.11, Java 21, JUnit 5, Shadow, and
  SQLite dependencies.
- The destination checkout is an untracked project directory under
  `/home/jlo/dev`; unrelated existing files must not be reset, staged, or
  deleted.

## Architecture

### API

Move `MailMessage`, `MailboxView`, `MailService`, and `SendMailResult` to
`dev.jlo.extras.api`. Preserve the Bukkit-free SPI, immutable mailbox view, and
all existing method contracts.

### Core

Move `MailRepository`, `DefaultMailService`, and `SqliteMailRepository` to
`dev.jlo.extras.core`. Preserve input validation, pagination clamping, newest-
first ordering, recipient scoping, atomic attachment claiming, and the
read/claimed bulk-delete rule. Keep the existing `mail` table schema and store
mail at `<ExtrasPlugin data directory>/mailbox/mailbox.db`.

### Paper

Move `MailCommand`, `MailboxGui`, `ComposeGui`, and `MailboxItemCodec` to
`dev.jlo.extras.paper`. Update imports and lifecycle Javadocs to reference
`ExtrasPlugin`. Remove the standalone `MailboxPlugin` and the unused static
command-registration helper; `/mail` is registered by `ExtrasPlugin` in its
existing `LifecycleEvents.COMMANDS` handler.

### Plugin lifecycle

`ExtrasPlugin.onEnable()` will:

1. create/validate the plugin data directory as it already does;
2. construct the mailbox repository and service using `mailbox/mailbox.db`;
3. register `MailService` with Bukkit's `ServicesManager` using the existing
   normal priority;
4. register the mailbox GUI and compose listeners against `ExtrasPlugin`; and
5. register `/mail` beside `/party`, `/friend`, and `/title` in the existing
   lifecycle command registrar.

`ExtrasPlugin.onDisable()` will unregister the plugin's listeners/services and
close the mailbox repository, nulling mailbox state just like the existing
party/friend services. Existing domain shutdown behavior remains unchanged.

## Descriptor and build

Keep the destination's `ModularExtras` descriptor and single-module Gradle
build. `paper-plugin.yml` will continue to declare only
`main: dev.jlo.extras.ExtrasPlugin`; no standalone mailbox descriptor or second
main class will be copied. The destination's existing SQLite dependency is
sufficient.

## Tests and verification

Port the mailbox service, repository, and item-codec tests into the matching
`dev.jlo.extras` packages while retaining all destination tests. Verification
will run the destination JUnit suite and shaded build. The Paper item-codec
suite may retain its existing headless-server assumptions; the real command and
plugin lifecycle path will be exercised by compilation/build and the existing
Paper run configuration where available.

## Non-goals

- Do not delete or rewrite files in `modular-mailbox`.
- Do not merge mailbox data into party/friend databases.
- Do not change mailbox commands, permissions, persistence schema, GUI behavior,
  or attachment encoding.
- Do not refactor unrelated destination domains.
