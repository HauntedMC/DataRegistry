# DataRegistry

DataRegistry is a shared player-data backend for HauntedMC plugins on Bukkit and Velocity.
It keeps player identity, online status, connection metadata, and session lifecycle data in sync across the network.

## Highlights

- Single backend core with thin platform boot layers.
- Privacy-first defaults (`ip`/`virtual-host` persistence is disabled by default).
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
- `database` (`type`, `connection-id`)
- `orm` (`schema-mode`)
- `privacy` (`persist-ip-address`, `persist-virtual-host`)
- `platform.bukkit` (`join-delay-ticks`)
- `validation` (`username/server/virtual-host/ip` max lengths)

Example:

```yaml
database:
  type: MYSQL
  connection-id: player_data_rw
orm:
  schema-mode: validate
privacy:
  persist-ip-address: false
  persist-virtual-host: false
```

Invalid values are rejected and safe defaults are used.

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
