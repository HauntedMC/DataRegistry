# DataRegistry

DataRegistry is HauntedMC's shared read/write boundary for canonical player identity and DataRegistry-owned
player metadata on Velocity and Paper.

It owns player creation, username updates, active identity state, connection metadata, language and nickname
preferences, playtime summaries, name history, and service-registry state. Feature plugins own their own tables
and should reference players by the stable scalar `playerId`.

## Runtime

- Velocity is the authoritative writer for joins, switches, disconnects, sessions, connection info, and probes.
- Paper prepares backend identity state and exposes the same read APIs to Paper features.
- DataProvider supplies database connections and ORM bootstrap.
- Production schemas must be migration-managed. Do not rely on Hibernate schema mutation in production.

## Requirements

- Java 25
- Maven 3.8.6+
- DataProvider `2.0.0`
- Velocity `4.0.0-SNAPSHOT` and/or Paper `26.1.2+`

## Configuration

Start the server once to generate `plugins/DataRegistry/config.yml`, then review the database, feature,
privacy, playtime, service-registry, and platform sections.

Defaults and comments live in [src/main/resources/config.yml](src/main/resources/config.yml). Missing supported
keys are restored on load and stale keys are removed.

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

Use `DataRegistry#players()` for player data:

```java
PlayerData players = platformPlugin.getDataRegistry().players();

players.whenReady(player.getUniqueId()).thenAccept(identity -> {
    identity.ifPresent(value -> {
        long playerId = value.playerId();
        UUID uuid = value.uuid();
        String username = value.username();
    });
});
```

### Identity

Use `whenReady(uuid)` in join paths. It completes when DataRegistry has finished the authoritative lifecycle
initialization for that player, including creation or username update if needed.

Use lookup-only methods outside lifecycle paths:

- `players.findIdentity(uuid)`, `players.findIdentityByUsername(name)`, and `players.findIdentity(playerId)`
- `players.findIdentityByIdentifier(identifier)` for command input that may be a UUID or username
- `players.findPlayerId(uuid)`, `players.findPlayerIdByUsername(name)`, and `players.findPlayerIdByIdentifier(identifier)`
- `players.findIdentitiesByUsernamePrefix(prefix, limit)` for suggestions and staff tooling

`PlayerIdentity` is immutable and standalone. It is safe to pass between feature layers and does not expose
Hibernate-managed state.

### Player Profiles

Use `PlayerProfile` when a feature needs a read snapshot of several DataRegistry-owned fields:

```java
players.findProfileByIdentifier(input, 20).ifPresent(profile -> {
    PlayerIdentity identity = profile.identity();
    Optional<String> nickname = profile.nickname();
    List<PlayerNameHistoryEntry> names = profile.nameHistory();
});
```

Profiles may include language, nickname, connection, online, activity, playtime, and name-history data depending
on enabled modules and available rows. Missing optional feature data is represented as `Optional.empty()` or an
empty list.

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

`find*` methods may perform database I/O. Run them asynchronously on Paper and Velocity event paths. `whenReady`
may complete on a DataRegistry lifecycle thread, so schedule platform API work back onto the platform thread when
required.

Downstream plugins must not create, update, or merge canonical player rows. They may write only through the
narrow DataRegistry methods for DataRegistry-owned preferences such as language and nickname.

## Data Ownership

Keep feature-owned data such as vanish, glow, nametags, friends, sanctions, client info, 2FA, voting, messaging,
and logs in the owning feature plugin. Do not move those records into DataRegistry. Prefer scalar `player_id`
references for new feature-owned tables and keep feature queries in the owning feature.

## Build

Authenticated GitHub Packages access may be required for private HauntedMC dependencies. Configure repository id
`github` in `~/.m2/settings.xml`, then run:

```bash
mvn clean test checkstyle:check
```

Build output:

- `target/DataRegistry.jar`

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
