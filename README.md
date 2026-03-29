# DataRegistry

[![CI Tests and Coverage](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-tests-and-coverage.yml)
[![CI Lint](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/DataRegistry/actions/workflows/ci-lint.yml)
[![Release](https://img.shields.io/github/v/release/HauntedMC/DataRegistry)](https://github.com/HauntedMC/DataRegistry/releases)
[![License](https://img.shields.io/github/license/HauntedMC/DataRegistry)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-007396)](https://adoptium.net/)

Shared player and service-state storage for HauntedMC across Velocity and Paper.

`DataRegistry` keeps identity, presence, sessions, rename history, and service-registry data in one place so other plugins can read a consistent view of the network.

## What It Does

- Stores player identity by UUID
- Tracks online status and current server
- Records session open and close events
- Optionally stores IP address and virtual host data
- Preserves former usernames in a dedicated history table
- Tracks services, running instances, and backend probe results

## Runtime Model

- Velocity is the authoritative writer for joins, switches, disconnects, sessions, connection info, and backend probes.
- Bukkit/Paper runs as a bridge so backend plugins can read the same registry and publish service heartbeats.
- DataProvider is required for connection management and ORM bootstrap.

Running only Bukkit/Paper without Velocity is not a supported deployment mode.

## Requirements

- Java 21
- Maven 3.8.6+
- DataProvider `2.0.0`
- Velocity `3.5.0-SNAPSHOT` and/or Paper `1.21.11-R0.1-SNAPSHOT`

## Install

Server setup:

1. Install `DataProvider`.
2. Build or download `DataRegistry.jar`.
3. Put both jars in the server `plugins/` directory.
4. Start the server once to generate `plugins/DataRegistry/config.yml`.
5. Configure the player and service database profiles to match your DataProvider setup.

If you run backend servers, set `platform.bukkit.service-name` to the Velocity server name instead of leaving it on `auto`. That keeps service identity stable across restarts.

## Configuration

The config is intentionally small. Most deployments only need to review:

- `database.profiles.players.connection-id`
- `database.profiles.services.connection-id`
- `orm.schema-mode`
- `privacy.persist-ip-address`
- `privacy.persist-virtual-host`
- `features.*`
- `service-registry.*`
- `platform.bukkit.service-name`
- `platform.velocity.service-name`

Defaults and inline comments live in [src/main/resources/config.yml](src/main/resources/config.yml). Missing supported keys are restored on load and stale keys are removed.

## Dependency Information

Coordinates:

- `groupId`: `nl.hauntedmc.dataregistry`
- `artifactId`: `dataregistry`
- `version`: `VERSION_HERE`

Repository:

- `https://maven.pkg.github.com/HauntedMC/DataRegistry`

Maven:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/HauntedMC/DataRegistry</url>
</repository>
```

```xml
<dependency>
  <groupId>nl.hauntedmc.dataregistry</groupId>
  <artifactId>dataregistry</artifactId>
  <version>VERSION_HERE</version>
  <scope>provided</scope>
</dependency>
```

Gradle (Groovy):

```groovy
compileOnly "nl.hauntedmc.dataregistry:dataregistry:VERSION_HERE"
```

## Use the API

From Bukkit/Paper:

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("DataRegistry");
if (!(plugin instanceof PlatformPlugin platformPlugin)) {
    throw new IllegalStateException("DataRegistry is unavailable.");
}

DataRegistry registry = platformPlugin.getDataRegistry();
Optional<PlayerEntity> player = registry.getPlayerRepository().findByUUID(uuid);
```

Primary entry points:

- `DataRegistry#getPlayerRepository()`
- `DataRegistry#getPlayerNameHistoryRepository()`
- `DataRegistry#getNetworkServiceRepository()`
- `DataRegistry#getServiceInstanceRepository()`
- `DataRegistry#getServiceProbeRepository()`
- `DataRegistry#newServiceRegistryService()`

## Build

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
