# Player Trading Design

## Goal

Add an item-for-item `/trade <player>` workflow to the Paper plugin. Two online players can review offers in a shared trade session, confirm independently, and exchange the offered `ItemStack`s only after both confirmations succeed.

## Scope

In scope:

- Item-for-item trading between two online players.
- A Bukkit-free `TradeService` state machine for request, accept, decline, cancel, offer snapshots, confirmation, and completion.
- A Paper inventory GUI for editing offers and confirming the exchange.
- `/trade <player>` command registration and tab completion.
- Safe cancellation and return of offered items on close, disconnect, decline, or invalid completion.
- Focused tests for all service state transitions and invalid operations.

Out of scope:

- Currency, economy plugins, permissions, claims, experience, or arbitrary plugin assets.
- Persisting active trades across server restarts.
- Trade history, auction listings, or offline trade requests.

## Design decisions

### Service boundary

`TradeService` is Bukkit-free. It uses UUIDs, immutable trade snapshots, and result enums. It never references `Player`, `ItemStack`, inventories, Bukkit events, or JDBC. Active trades are transient in-memory state because live item stacks cannot be resumed safely after a restart.

The service owns participant identity, request lifecycle, confirmation state, and one active trade per player. The Paper adapter owns the current GUI inventory and supplies immutable offer snapshots to the service when confirmation or completion is requested.

### State machine

A player may have at most one active request/trade. A request targets two distinct players and is accepted by the target. Accepted requests become an active trade with both confirmations false. Either participant may cancel. Either participant may confirm their current offers. Any offer change resets both confirmations. Completion succeeds only when both participants confirmed and the service receives the same current offer versions for both sides.

Results explicitly distinguish success, self-trade, duplicate request, already trading, missing request, not a participant, stale offer, unconfirmed trade, and already completed/cancelled state. Failed operations preserve state.

### GUI

`TradeGui` creates a private inventory view containing a left offer region, a right offer region, and confirmation controls. Each player sees their own offer on their side and the other player's offer read-only. Clicks and drags affecting the top inventory are cancelled unless they are valid offer-region interactions for that viewer. Any offer mutation clears both confirmations. A close event cancels the trade and returns both sides' items. Disconnect and plugin disable use the same cancellation path.

The GUI is identified by a stable title prefix and stores sessions by trade/player UUID. It must never trust slot contents supplied by a client: completion reads the authoritative top inventory, validates the expected offer regions and amounts, then removes the exact stacks only after all checks succeed.

### Completion and item safety

The completion path validates that both players remain online, the trade remains active, both confirmations are true, and both offer inventories still contain the expected stacks. It then removes the offered stacks, adds each side's items to the other player's inventory, and safely handles `addItem` leftovers by dropping them at the recipient's location. If validation fails before removal, the trade remains active and confirmations are cleared where appropriate. If cancellation occurs, each side's offer is returned to its owner, with overflow dropped naturally.

### Plugin integration

`ExtrasPlugin` constructs and registers the service only as an internal dependency of the Paper command and GUI. It registers `/trade` during `LifecycleEvents.COMMANDS`, unregisters listeners and closes active sessions on disable, and updates plugin description text to include trading. No existing service contract changes.

## Testing

Service tests use real in-memory state and assert observable transitions: self-request rejection, duplicate requests, accept/decline/cancel, one-active-trade enforcement, participant authorization, confirmation invalidation after offer version changes, successful completion, stale-offer rejection, and repeat-completion rejection. GUI logic is covered by focused tests for any Bukkit-free helpers; the Paper run smoke test opens `/trade`, moves an item, confirms from both players, and verifies the inventories exchange items.
