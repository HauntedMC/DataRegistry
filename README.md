# DataRegistry

DataRegistry is HauntedMC's shared read/write boundary for canonical player identity and DataRegistry-owned
player metadata on Velocity and Paper.

It owns player creation, username updates, active identity state, connection metadata, language and nickname
preferences, playtime summaries, and name history. Feature plugins own their own tables and should reference
players by the stable scalar `playerId`.

## Runtime

- Velocity is the authoritative writer for joins, switches, disconnects, sessions, connection info, and probes.
- Paper prepares backend identity state and exposes the same read APIs to Paper features.
- DataProvider supplies database connections and ORM bootstrap.
- Production schemas must be migration-managed. Do not rely on Hibernate schema mutation in production.
- On Velocity startup, stale player presence from an unclean shutdown is reconciled before periodic flushing starts.
  Open sessions, visits, playtime segments, and online flags are closed from the last durable activity timestamp instead
  of from startup time.
- On Velocity shutdown, queued player lifecycle writes are drained before active players are persisted offline.

## Requirements

- Java 25
- Maven 3.8.6+
- DataProvider `2.1.4`
- Velocity `4.0.0-SNAPSHOT` and/or Paper `26.1.2+`

## Configuration

Start the server once to generate `plugins/DataRegistry/config.yml`, then review the database, feature,
privacy, playtime, service-registry, and platform sections.

Defaults and comments live in [dataregistry-core/src/main/resources/config.yml](dataregistry-core/src/main/resources/config.yml). Missing supported
keys are restored on load and stale keys are removed.

## Developer API

Depend only on `dataregistry-api` as `provided`:

```xml
<dependency>
  <groupId>nl.hauntedmc.dataregistry</groupId>
  <artifactId>dataregistry-api</artifactId>
  <version>1.10.4</version>
  <scope>provided</scope>
</dependency>
```

Use `DataRegistryApi#players()` for player data:

```java
DataRegistryApiProvider apiProvider = /* platform plugin instance */;
PlayerData players = apiProvider.getDataRegistry().players();

UUID uuid = player.getUniqueId(); // snapshot platform state before async continuations
players.whenReady(uuid).thenAccept(identity -> {
    identity.ifPresent(value -> {
        long playerId = value.playerId();
        UUID canonicalUuid = value.uuid();
        String username = value.username();
    });
});
```

### Artifact boundaries

- `dataregistry-api` is the only dependency for ProxyFeatures, ServerFeatures, and other consumers. It has no
  DataProvider, Hibernate/Jakarta Persistence, Velocity, or Paper dependency.
- `dataregistry-core` owns entities, repositories, ORM wiring, lifecycle writers, recovery, and query execution.
  It is an implementation dependency of the platform modules, never a feature dependency.
- `dataregistry-platform-velocity` owns authoritative proxy lifecycle listeners, including
  `PlayerStatusListener`; `dataregistry-platform-paper` provides the Paper identity bridge.
- `dataregistry-migrations` contains ordered schema migration resources; the default core schema mode is `validate`.
- `dataregistry-testkit` supplies `FakeDataRegistryApi`, immutable player fixtures, temporary IDs, and async failure
  simulation for feature contract tests.

`DataRegistryApiProvider#getDataRegistry()` returns `DataRegistryApi`, not the core runtime. Platform plugins
implement that provider capability; consumers can depend on `dataregistry-api` alone. There is deliberately no
public path from that type to an ORM context, entity, repository, lifecycle writer, or DataProvider handle.

Feature maintainers migrating from the former monolithic artifact should follow
[DOWNSTREAM_MIGRATION.md](DOWNSTREAM_MIGRATION.md). A dependency-coordinate change alone is not valid for a feature
that currently maps `PlayerEntity` in its own ORM model.

### Identity

Use `whenReady(uuid)` in join paths. It completes when DataRegistry has finished the authoritative lifecycle
initialization for that player, including creation or username update if needed.

Use lookup-only methods outside lifecycle paths:

- `players.findIdentity(uuid)`, `players.findIdentityByUsername(name)`, and `players.findIdentity(playerId)`
- `players.findIdentityByIdentifier(identifier)` for command input that may be a UUID or username
- `players.findPlayerId(uuid)` and `players.findPlayerIdByIdentifier(identifier)`
- `players.findIdentities(lookups)` for bulk identity resolution
- `players.findIdentitiesByUsernamePrefix(prefix, pageRequest)` for cursor-based suggestions and staff tooling
- `players.findActiveIdentityCached(uuid)` only when cache-only behavior is explicitly acceptable

`PlayerIdentity` is immutable and standalone. It is safe to pass between feature layers and does not expose
Hibernate-managed state.

### Player Profiles

Use `PlayerProfile` when a feature needs a read snapshot of several DataRegistry-owned fields:

```java
players.findProfileByIdentifier(input, 20).thenAccept(profileOpt -> profileOpt.ifPresent(profile -> {
    PlayerIdentity identity = profile.identity();
    Optional<String> nickname = profile.nickname();
    List<PlayerNameHistoryEntry> names = profile.nameHistory();
}));
```

Profiles may include language, nickname, connection, online, activity, playtime, and name-history data depending
on enabled modules and available rows. Missing optional feature data is represented as `Optional.empty()` or an
empty list. Profile projection is assembled by DataRegistry in one transaction for a consistent snapshot.

### Feature Reads

Use the specific facade methods when a full profile is unnecessary:

- `players.findLanguage(playerId)` and `players.saveLanguage(playerId, preference, effective)`
- `players.findNickname(playerId)` and `players.saveNickname(playerId, nickname)`
- `players.findConnection(playerId)`
- `players.findOnlinePlayers(limit)`
- `players.findActivity(playerId)`
- `players.findPlaytime(playerId)` and leaderboard helpers
- `players.findNameHistory(playerId, limit)`
- `players.findIdentitiesSharingLastIp(playerId)` and `players.findUsernamesSharingLastIp(playerId)`
- `players.findPlayerIdsByLastIpAddress(ip, excludePlayerId)` and `players.findUsernamesByLastIpAddress(ip, excludePlayerId)`

Public persistence reads and DataRegistry-owned preference writes return `CompletionStage` and run on DataRegistry's
query executor with configured deadlines. Returned futures support cancellation when used as `CompletableFuture`.
Development thread checks warn when likely event threads request queries or block pending query stages. Completion
callbacks may run on DataRegistry worker or lifecycle threads, so snapshot Bukkit/Velocity state before starting async
work and schedule platform API work back onto the platform thread when required.

Downstream plugins must not create, update, or merge canonical player rows. They may write only through the
narrow DataRegistry methods for DataRegistry-owned preferences such as language and nickname.

### Feature Services

DataRegistry also exposes a process-local service catalog for feature-owned APIs. This lets enabled features share
their own data and behavior without moving their tables into DataRegistry or forcing consumers to query another
feature's ORM entities.

Feature plugins should publish narrow interfaces from their own lifecycle code:

```java
FeatureServiceHandle handle = dataRegistry.featureServices().register(
        "ServerFeatures",
        "Vanish",
        VanishAPI.class,
        vanishService
);
```

Consumers should resolve feature services by interface:

```java
dataRegistry.featureServices()
        .find(VanishAPI.class)
        .ifPresent(vanish -> vanish.isVanished(playerId));
```

Use `find` for optional integrations and `require` only when a feature cannot run without the dependency. Close the
returned `FeatureServiceHandle` during feature disable, or use the ServerFeatures/ProxyFeatures lifecycle API
manager, which publishes and unregisters services automatically.

The catalog is intentionally runtime-only. It does not provide cross-server RPC, cache persistence, or schema
ownership. Exported interfaces should be stable, small, and expressed in scalar IDs or immutable value objects where
possible.

## Data Ownership

Keep feature-owned data such as vanish, glow, nametags, friends, sanctions, client info, 2FA, voting, messaging,
and logs in the owning feature plugin. Do not move those records into DataRegistry. Prefer scalar `player_id`
references for new feature-owned tables and keep feature queries in the owning feature.

Feature-owned services are the supported sharing boundary for that data. For example, a messaging feature may ask
the Vanish feature whether a `playerId` is hidden, but it should not read or join the vanish table directly.

## Build

Authenticated GitHub Packages access may be required for private HauntedMC dependencies. Configure repository id
`github` in `~/.m2/settings.xml`, then run:

```bash
mvn clean test package
```

Build output:

- `dataregistry-api/target/dataregistry-api-*.jar`
- `dataregistry-core/target/dataregistry-core-*.jar`
- `dataregistry-platform-velocity/target/dataregistry-platform-velocity-*-bundled.jar`
- `dataregistry-platform-paper/target/dataregistry-platform-paper-*-bundled.jar`

Deploy the `bundled` platform JAR only. It embeds the platform's relocated core implementation while retaining the
public `DataRegistryApi` namespace. Do not deploy `dataregistry-core` as a separate server plugin and do not add it
as a dependency to feature plugins.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
