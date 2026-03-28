# DataRegistry

DataRegistry is a shared player-data backend for HauntedMC plugins on Bukkit and Velocity.
It keeps player identity, online status, connection metadata, and session lifecycle data in sync across the network.

## Highlights

- Single backend core with thin platform boot layers.
- Privacy-first defaults (`ip`/`virtual-host` persistence is disabled by default).
- Feature-gated built-in domains (`online-status`, `connection-info`, `sessions`, `name-history`, `service-registry`).
- Domain-level database profile split (`database.profiles.players` and `database.profiles.services`).
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

Key sections:
- `database` (`type`, `profiles.players.connection-id`, `profiles.services.connection-id`)
- `orm` (`schema-mode`)
- `privacy` (`persist-ip-address`, `persist-virtual-host`)
- `features` (`online-status`, `connection-info`, `sessions`, `name-history`, `service-registry`)
- `service-registry` (`heartbeat-interval-seconds`)
- `platform.bukkit` (`join-delay-ticks`)
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
```

Invalid values are rejected and safe defaults are used.

## Feature Toggles

Built-in domains can be enabled/disabled without code changes:

- `features.online-status`: controls `player_online_status` writes.
- `features.connection-info`: controls `player_connection_info` writes.
- `features.sessions`: controls `player_sessions` writes.
- `features.name-history`: controls `player_name_history` writes.
- `features.service-registry`: controls `network_service` / `service_instance` writes and heartbeat updates.

`player_entity` is always enabled because all other domains depend on identity records.

Connection routing:
- `database.profiles.players.connection-id` is used for player tables.
- `database.profiles.services.connection-id` is used for service registry tables.
- When both IDs match, DataRegistry reuses a single registered DataProvider connection.

## Service Registry Helper API

Other feature modules can use the built-in helper facade instead of writing custom ORM queries:

- `DataRegistry#newServiceRegistryService()`
- `listServices(...)`, `findService(...)`
- `listInstances(...)`, `listRunningInstances()`, `findInstance(...)`
- `findMostRecentRunningInstance(...)`, `resolveEndpoint(...)`
- `isInstanceActiveWithin(...)`, `listStaleRunningInstances(...)`
- `countRunningInstancesByKind()`, `listServiceHealth()`

The helper returns immutable read views for safe cross-feature consumption.

## Repository Helpers

Built-in repositories also expose read helpers so features do not need to duplicate ORM queries:

- `PlayerRepository`: `findByUsername(...)`, `findByUsernamePrefix(...)`, `findByUUIDs(...)`, active-cache snapshot/count helpers.
- `PlayerSessionRepository`: latest/open/recent lookups, time-window queries, open-session counts.
- `PlayerNameHistoryRepository`: latest lookup, player+username lookup, recent-by-player, recent-by-username.
- `NetworkServiceRepository`: kind/name existence checks, service-name lookups, recency filters, kind counts.
- `ServiceInstanceRepository`: running/by-service lookups, stale/fresh recency filters, status and per-service counters.
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
