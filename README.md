# DataRegistry

DataRegistry is a shared player-data backend for HauntedMC plugins on Bukkit and Velocity.
It keeps player identity, online status, connection metadata, and session lifecycle data in sync across the network.

## Highlights

- Single backend core with thin platform boot layers.
- Privacy-first defaults (`ip`/`virtual-host` persistence is disabled by default).
- Configurable limits and database wiring through `config.yml`.
- Transaction-safe session/status updates with defensive validation.

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
