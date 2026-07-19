# DataRegistry

DataRegistry is HauntedMC's shared registry for canonical player identity, player-related network state,
playtime, connection metadata, and service-registry state on Velocity and Paper.

Feature plugins should keep their own feature data in their own tables. Reference DataRegistry players by the
stable `playerId`; do not create or update canonical player rows from feature code.

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

Common reads:

- `players.findIdentity(uuid)` and `players.findIdentityByUsername(name)`
- `players.findPlayerId(uuid)`
- `players.findLanguage(playerId)` and `players.saveLanguage(playerId, preference, effective)`
- `players.findNickname(playerId)` and `players.saveNickname(playerId, nickname)`
- `players.findConnection(playerId)`
- `players.findNameHistory(playerId, limit)`
- `players.findOnlinePlayers(limit)`
- `players.findActivity(playerId)`
- `players.findPlaytime(playerId)` and leaderboard helpers
- `players.findPlayerIdsByLastIpAddress(ip, excludePlayerId)` for moderation workflows

`find*` methods may perform database I/O. Run them asynchronously on Paper or Velocity event paths.
`whenReady` observes lifecycle state and may complete on a DataRegistry lifecycle thread, so schedule platform
API work back onto the server thread when required.

Player creation, username updates, active-player cache updates, sessions, activity summaries, and connection
tracking are lifecycle-owned. Downstream plugins should only read those values or use the narrow DataRegistry
preference methods for DataRegistry-owned language and nickname data.

## Data Ownership

Keep feature-owned data such as vanish, glow, nametags, friends, sanctions, client info, 2FA, voting, messaging,
and logs in the owning feature plugin. Prefer scalar `player_id` references for new feature-owned tables.

## Build

Authenticated GitHub Packages access may be required for private HauntedMC dependencies. Configure repository id
`github` in `~/.m2/settings.xml`, then run:

```bash
mvn test
mvn checkstyle:check
```

Build output:

- `target/DataRegistry.jar`

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
