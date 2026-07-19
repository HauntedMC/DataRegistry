# DataRegistry

[![CI Tests and Coverage](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-tests-and-coverage.yml)
[![CI Lint](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-lint.yml)
[![Release](https://img.shields.io/github/v/release/HauntedMC/DataRegistry)](https://github.com/HauntedMC/DataRegistry/releases)
[![License](https://img.shields.io/github/license/HauntedMC/DataRegistry)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-007396)](https://adoptium.net/)

DataRegistry is HauntedMC's shared player identity and service-state registry for Velocity and Paper.

It owns canonical player rows, current usernames, online/session state, rename history, playtime, connection metadata, and service-registry data. Feature plugins should keep their own feature data in their own tables and reference DataRegistry players by the stable `playerId`.

## Runtime Model

- Velocity is the authoritative writer for joins, switches, disconnects, sessions, connection info, and backend probes.
- Paper runs as a bridge for backend identity initialization, read access, and optional service heartbeats.
- DataProvider supplies database connections and ORM bootstrap.
- Running only Paper without Velocity is not a supported production topology.

## Requirements

- Java 25
- Maven 3.8.6+
- DataProvider `2.0.0`
- Velocity `4.0.0-SNAPSHOT` and/or Paper `26.1.2+`

## Configuration

Start the server once to generate `plugins/DataRegistry/config.yml`, then review:

- `database.profiles.players.connection-id`
- `database.profiles.services.connection-id`
- `orm.schema-mode`
- `privacy.persist-ip-address`
- `privacy.persist-virtual-host`
- `features.*`
- `playtime.*`
- `service-registry.*`
- `platform.bukkit.service-name`
- `platform.velocity.service-name`

Use an explicit, migration-managed schema mode in production. Do not rely on Hibernate to mutate production schemas automatically.

Defaults and inline comments live in [src/main/resources/config.yml](src/main/resources/config.yml). Missing supported keys are restored on load and stale keys are removed.

## Developer API

Depend on DataRegistry as `provided`:

```xml
<dependency>
  <groupId>nl.hauntedmc.dataregistry</groupId>
  <artifactId>dataregistry</artifactId>
  <version>1.9.6</version>
  <scope>provided</scope>
</dependency>
```

Use `PlayerDirectory` for player identity reads:

```java
DataRegistry registry = platformPlugin.getDataRegistry();
PlayerDirectory players = registry.getPlayerDirectory();

players.whenReady(player.getUniqueId()).thenAccept(identity -> {
    identity.ifPresent(playerIdentity -> {
        Long playerId = playerIdentity.playerId();
        UUID uuid = playerIdentity.uuid();
        String username = playerIdentity.username();
    });
});
```

Primary player identity methods:

- `PlayerDirectory#whenReady(UUID)` waits for DataRegistry join initialization without doing database work.
- `PlayerDirectory#getActiveIdentity(UUID)` and `PlayerDirectory#getActiveIdentity(String)` read the active in-memory identity only.
- `PlayerDirectory#findByUuid(UUID)` and `PlayerDirectory#findByUuid(String)` look up persisted identities without creating players.
- `PlayerDirectory#findByUsername(String)` and `PlayerDirectory#findByUsernameIgnoreCase(String)` look up persisted identities without updating usernames.
- `PlayerDirectory#snapshotActiveIdentities()` returns immutable active identity snapshots keyed by UUID string.

Lookup methods can perform database I/O. Call them from an async context on Velocity/Paper event paths.
`whenReady` callbacks may run on a DataRegistry lifecycle thread; schedule Bukkit/Paper API work back onto
the server thread. Player creation and username updates are intentionally not exposed to feature plugins;
DataRegistry lifecycle code is the only writer for canonical player identity.

## Data Ownership

DataRegistry stores canonical shared state only. Keep feature-owned data such as vanish, glow, nametags, friends, sanctions, client info, 2FA, voting, messaging, and logs in the owning feature plugin.

For new feature tables, prefer scalar `player_id` references when possible. Existing feature tables and foreign keys should be migrated conservatively and never destructively.

## Build

Authenticated GitHub Packages access may be required for private HauntedMC dependencies. Configure repository id `github` in `~/.m2/settings.xml`, then run:

```bash
mvn test
mvn verify
```

Build output:

- `target/DataRegistry.jar`

## Project Info

- [Contributing](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security](SECURITY.md)
- [Support](SUPPORT.md)

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
