# DataRegistry

DataRegistry is a shared player-data backend for HauntedMC plugins on Bukkit and Velocity.
It keeps player identity, online status, connection metadata, and session lifecycle data in sync across the network.

## Highlights

- Single backend core with thin platform boot layers.
- Privacy-first defaults (`ip`/`virtual-host` persistence is disabled by default).
- Feature-gated built-in domains (`online-status`, `connection-info`, `sessions`, `name-history`, `service-registry`).
- Name-history timeline preserves repeated names (`A -> B -> C -> A`) in a single history table.
- Domain-level database profile split (`database.profiles.players` and `database.profiles.services`).
- Velocity-driven backend probe tracking (`service_probe`) with effective health classification helpers.
- Configurable limits and database wiring through `config.yml`.
- Transaction-safe session/status updates with defensive validation.

## Deployment Model

DataRegistry is designed for a network topology where Velocity is present.

- Velocity plugin: authoritative lifecycle writer (status, connection info, sessions).
- Bukkit/Paper plugin: identity bridge/cache for backend plugin access.

Running only Bukkit/Paper without Velocity is not a supported deployment mode.

## Architecture

DataRegistry is intentionally layered so platform code stays thin:

- `api/`: ORM entities, repository contracts, and `DataRegistry` runtime bootstrap.
- `backend/config/`: immutable validated settings and safe YAML loading.
- `backend/service/`: business lifecycle services (identity, status, connection info, sessions).
- `platform/*/`: Bukkit/Velocity adapters, listeners, and logger bridges.
- `platform/internal/lifecycle/`: shared runtime holder for safe start/stop transitions.

Service classes only depend on `DataRegistry` + logger abstractions and are unit-testable in isolation.
Platform listeners are responsible for extracting live platform data and invoking services.

## Extending DataRegistry

When adding new data domains, follow this order:

1. Add a JPA entity in `api/entities`.
2. Add a repository in `api/repository`.
3. Add a focused service in `backend/service` (validation + transactional behavior).
4. Wire it in platform startup only where needed (`VelocityDataRegistry` and/or `BukkitDataRegistry`).
5. Add unit tests for repository + service behavior, then listener integration tests.

Keep persistence decisions (privacy, limits, schema behavior) configurable via `DataRegistrySettings` so platform code remains configuration-agnostic.

## Requirements

- Java 21
- Maven 3.8.6+
- HauntedMC DataProvider `2.0.0`

## Configuration

On first start, DataRegistry writes a `config.yml` to the plugin data directory.
On every load, it also reconciles the file to the expected schema:
- Missing supported keys are added with effective/default values.
- Unknown or outdated keys are removed.

Key sections:
- `database` (`type`, `profiles.players.connection-id`, `profiles.services.connection-id`)
- `orm` (`schema-mode`)
- `privacy` (`persist-ip-address`, `persist-virtual-host`)
- `features` (`online-status`, `connection-info`, `sessions`, `name-history`, `service-registry`)
- `service-registry` (`heartbeat-interval-seconds`, `probe-interval-seconds`, `probe-timeout-millis`, `probe-retention-hours`)
- `platform.bukkit` (`join-delay-ticks`)
- `platform.bukkit` / `platform.velocity` (`service-name`)
- `validation` (`username/server/virtual-host/ip` max lengths)

Example:

```yaml
database:
  type: MYSQL
  profiles:
    players:
      connection-id: player_data_rw
    services:
      connection-id: player_data_rw
orm:
  schema-mode: validate
privacy:
  persist-ip-address: false
  persist-virtual-host: false
features:
  online-status: true
  connection-info: true
  sessions: true
  name-history: true
  service-registry: true
service-registry:
  heartbeat-interval-seconds: 30
  probe-interval-seconds: 15
  probe-timeout-millis: 1500
  probe-retention-hours: 168
platform:
  bukkit:
    service-name: auto
  velocity:
    service-name: auto
```

Invalid values are rejected and safe defaults are used.

### ORM Schema Modes (`orm.schema-mode`)

- `validate`: validates mappings against existing schema only. Use for production.
- `update`: attempts additive schema updates. Use for development/staging.
- `create`: drops/recreates schema on startup. Use only for ephemeral local environments.
- `create-drop`: creates schema on startup and drops it on shutdown. Use only for tests/local.
- `none`: disables ORM schema management entirely. Use when external migrations manage schema.

When upgrading to this version with `validate` or `none`, ensure your schema matches the current mappings, including:
- `player_name_history` (single-table former-name timeline model)
- `service_probe` (Velocity backend probe history)

### Setting Reference

- `database.type` (default `MYSQL`): DataProvider relational database type to register.
- `database.profiles.players.connection-id` (default `player_data_rw`): DataProvider connection profile for player-domain tables; must match `[A-Za-z0-9._-]{1,64}`.
- `database.profiles.services.connection-id` (default `player_data_rw`): DataProvider connection profile for service-registry tables; must match `[A-Za-z0-9._-]{1,64}`.
- `orm.schema-mode` (default `validate`): ORM schema management mode (`validate`, `update`, `create`, `create-drop`, `none`).
- `privacy.persist-ip-address` (default `false`): when `true`, stores IP addresses in `player_connection_info`.
- `privacy.persist-virtual-host` (default `false`): when `true`, stores virtual host values in `player_connection_info`.
- `features.online-status` (default `true`): enables `player_online_status` domain.
- `features.connection-info` (default `true`): enables `player_connection_info` domain.
- `features.sessions` (default `true`): enables `player_sessions` domain.
- `features.name-history` (default `true`): enables `player_name_history` (historical former-name timeline).
- `features.service-registry` (default `true`): enables `network_service` and `service_instance` domains.
- `service-registry.heartbeat-interval-seconds` (default `30`, range `5..300`): cadence for service instance heartbeat writes.
- `service-registry.probe-interval-seconds` (default `15`, range `5..300`): Velocity backend-probe cadence.
- `service-registry.probe-timeout-millis` (default `1500`, range `200..10000`): timeout per backend probe.
- `service-registry.probe-retention-hours` (default `168`, range `1..2160`): probe history retention; stale rows are pruned during probe passes.
- `platform.bukkit.join-delay-ticks` (default `4`, range `0..200`): delay after Bukkit join before status snapshot/writes.
- `platform.bukkit.service-name` (default `auto`): backend logical service identity; set explicitly to match Velocity server name.
- `platform.velocity.service-name` (default `auto`): proxy logical service identity.
- `validation.username.max-length` (default `32`, range `1..32`): max persisted username length.
- `validation.server.max-length` (default `64`, range `1..64`): max persisted server name length.
- `validation.virtual-host.max-length` (default `255`, range `1..255`): max persisted virtual host length.
- `validation.ip.max-length` (default `45`, range `7..45`): max persisted IP textual length.

## Feature Toggles

Built-in domains can be enabled/disabled without code changes:

- `features.online-status`: controls `player_online_status` writes.
- `features.connection-info`: controls `player_connection_info` writes.
- `features.sessions`: controls `player_sessions` writes.
- `features.name-history`: controls `player_name_history` writes.
- `features.service-registry`: controls `network_service` / `service_instance` writes and heartbeat updates.
- `service_probe` writes and effective health helpers are also gated by `features.service-registry`.

`player_entity` is always enabled because all other domains depend on identity records.

Name history model:
- `player_entity.username` stores the current username.
- `player_name_history` stores former usernames only.
- On detected rename at login, DataRegistry writes the previous username with `last_seen_at`.
- `last_seen_at` uses `player_connection_info.last_disconnect_at` when available, otherwise current time.

Connection routing:
- `database.profiles.players.connection-id` is used for player tables.
- `database.profiles.services.connection-id` is used for `network_service`, `service_instance`, and `service_probe`.
- When both IDs match, DataRegistry reuses a single registered DataProvider connection.

Service identity notes:
- Best practice: set `platform.bukkit.service-name` explicitly to the Velocity server name (`lobby-1`, `survival-2`, ...).
- With `platform.bukkit.service-name: auto`, DataRegistry falls back to `paper-<host>:<port>` naming and logs a warning.
- Velocity probe writes auto-correlate to recently seen running backend instances by endpoint (`host:port`) when possible, reducing mismatch risk from stale crash leftovers.

## Service Registry Helper API

Other feature modules can use the built-in helper facade instead of writing custom ORM queries:

- `DataRegistry#newServiceRegistryService()`
- `listServices(...)`, `findService(...)`
- `listInstances(...)`, `listRunningInstances()`, `findInstance(...)`
- `findMostRecentRunningInstance(...)`, `resolveEndpoint(...)`
- `isInstanceActiveWithin(...)`, `listStaleRunningInstances(...)`
- `countRunningInstancesByKind()`, `listServiceHealth()`
- `recordProbe(...)`, `findMostRecentProbe(...)`, `listRecentProbes(...)`
- `listRecentProbesByObserver(...)`, `countProbesByStatus()`
- `findMostRecentRunningInstanceByEndpoint(...)`, `findMostRecentRunningInstanceByEndpointWithin(...)`, `purgeProbesOlderThan(...)`
- `listServiceEffectiveHealth(...)`, `findServiceEffectiveHealth(...)`

The helper returns immutable read views for safe cross-feature consumption.

## Repository Helpers

Built-in repositories also expose read helpers so features do not need to duplicate ORM queries:

- `PlayerRepository`: `findByUsername(...)`, `findByUsernamePrefix(...)`, `findByUUIDs(...)`, active-cache snapshot/count helpers.
- `PlayerSessionRepository`: latest/open/recent lookups, time-window queries, open-session counts.
- `PlayerNameHistoryRepository`: former-name timeline lookups (latest, recent, and chronological by player).
- `NetworkServiceRepository`: kind/name existence checks, service-name lookups, recency filters, kind counts.
- `ServiceInstanceRepository`: running/by-service lookups, stale/fresh recency filters, status and per-service counters.
- `ServiceProbeRepository`: per-service latest/recent probe lookups, observer lookups, status counters.
- All repositories (via `AbstractRepository`) now include `findAll(limit)`, `existsById(...)`, and `count()`.

Contributor/maintainer guidance:
- `CONTRIBUTING.md`

## Testing

- Run the full unit test suite:

```bash
mvn test
```

- Run tests, Checkstyle, and coverage gates:

```bash
mvn verify
```

Coverage is enforced via JaCoCo during `verify` with minimum bundle thresholds:
- Line coverage: `75%`
- Branch coverage: `55%`

The HTML coverage report is generated at:
- `target/site/jacoco/index.html`
