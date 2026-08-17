# DataRegistry

DataRegistry is HauntedMC's shared read/write boundary for canonical player identity and DataRegistry-owned
player metadata on Velocity and Paper.

It owns player creation, username updates, active identity state, connection metadata, language and nickname
preferences, playtime summaries, population membership and counters, and name history. Feature plugins own their own
tables and should reference players by the stable scalar `playerId`.

## Runtime

- Velocity is the authoritative writer for joins, switches, disconnects, sessions, connection info, population state,
  and probes.
- Paper prepares backend identity state and exposes the same read APIs to Paper features.
- DataProvider supplies database connections and ORM bootstrap.
- Hibernate automatically applies additive schema updates by default.
- On Velocity startup, stale player presence from an unclean shutdown is reconciled before periodic flushing starts.
  Open sessions, visits, playtime segments, online flags, and derived population online counters are reconciled from
  durable state instead of inventing activity after the last known event.
- On Velocity shutdown, queued player lifecycle writes are drained before active players are persisted offline.

## Requirements

- Java 25
- Maven 3.8.6+
- Docker, for the container-backed and platform-acceptance suites
- DataProvider `3.1.8`
- Velocity `4.1.0-SNAPSHOT` and/or Paper `26.2`

Configure both the shell `JAVA_HOME` and the IDE Maven runner/importer to Java 25. The build deliberately rejects
Java 26 or newer until the bundled DataProvider/Hibernate stack is qualified for it.

## Configuration

Start the server once to generate `plugins/DataRegistry/config.yml`, then review the database, feature,
privacy, playtime/population mapping, retention, service-registry, and platform sections.

Defaults and comments live in [dataregistry-core/src/main/resources/config.yml](dataregistry-core/src/main/resources/config.yml). Missing supported
keys are restored on load and stale keys are removed.

The `population` domain is enabled by default. It requires `online-status`, `sessions`, and `session-visits`, which
provide the canonical presence and visit evidence used by Population. Population does **not** require playtime. It
reuses the existing `playtime.server-gamemode-rules`, ignored-gamemode policy, and unknown-server resolution so the
network has one server-to-logical-gamemode mapping rather than two competing mapping systems.

## Velocity administration

The Velocity command `/dataregistry` (alias `/dr`) requires `dataregistry.admin`.

- `/dataregistry status` shows runtime, player-count, and playtime-policy status.
- `/dataregistry features` lists enabled built-in data domains. Feature changes require a Velocity restart.
- `/dataregistry diagnostics` compares live and durable presence, reports playerbase/lifecycle state, and shows
  service-registry totals.
- `/dataregistry players online`, `players recent`, and `players inspect <name-or-uuid>` provide durable playerbase,
  activity, and profile views.
- `/dataregistry services health` reports effective service/probe health.
- `/dataregistry presence repair` force-refreshes durable online status from the players connected to this proxy. It
  never marks absent players offline, which keeps it safe for a shared multi-proxy database. Population online
  aggregates are reconciled from the resulting canonical status rows.
- `/dataregistry playtime status` shows the active flush interval plus ignored and network-total-excluded keys.
- `/dataregistry playtime mappings` shows the ordered server-to-gamemode mapping rules (first match wins) and the
  unknown-server fallback behavior.
- `/dataregistry playtime flush` queues an immediate playtime accrual flush for active players. It is useful before
  inspecting persisted totals; the command reports how many player queues accepted the flush.
- `/dataregistry playtime reconcile` reloads the playtime section of `config.yml` and applies it immediately. It
  updates the shared server-to-gamemode mapping, unknown-server handling, network-total exclusions, and flush cadence
  while retaining historic playtime and population membership records.

The command deliberately does not live-reload feature flags, database settings, or other non-playtime configuration;
restart Velocity for those changes.

The Velocity administration command uses the shared `hauntedmc-theme-palette` artifact, which is included in the bundled
plugin jar and does not add a runtime plugin dependency.

## Developer API

Depend only on `dataregistry-api` as `provided` (replace the version with the release you target):

```xml
<dependency>
  <groupId>nl.hauntedmc.dataregistry</groupId>
  <artifactId>dataregistry-api</artifactId>
  <version>1.14.0</version>
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
- `dataregistry-core` owns entities, repositories, ORM wiring, lifecycle writers, recovery, population reconciliation,
  and query execution. It is an implementation dependency of the platform modules, never a feature dependency.
- `dataregistry-platform-velocity` owns authoritative proxy lifecycle listeners, including
  `PlayerStatusListener`; `dataregistry-platform-paper` provides the Paper identity bridge.
- `dataregistry-testkit` supplies `FakeDataRegistryApi`, `FakePopulationData`, immutable player fixtures, temporary
  IDs, and async failure simulation for feature contract tests.

`DataRegistryApiProvider#getDataRegistry()` returns `DataRegistryApi`, not the core runtime. Platform plugins
implement that provider capability; consumers can depend on `dataregistry-api` alone. There is deliberately no
public path from that type to an ORM context, entity, repository, lifecycle writer, or DataProvider handle.

Feature maintainers migrating from an older DataRegistry API should follow
[DOWNSTREAM_MIGRATION.md](DOWNSTREAM_MIGRATION.md). DataRegistry 1.14.0 intentionally makes the Population facade part
of the required `DataRegistryApi` contract; custom API implementations and test fakes must implement it.

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
- `players.findGamemodeActivity(PlayerLookup, gamemodeKey)` for durable per-player lifecycle and playtime
- `players.findGamemodeStatistics(gamemodeKey)` for unique-player, playtime, and visit totals
- `players.findTrackedGamemodes()` for the central gamemode catalog and network-total policy
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

### Population

`DataRegistryApi#population()` is the canonical population boundary for network-wide and logical-gamemode player
counts. It is deliberately separate from playtime: playtime describes duration/activity, while Population describes
membership, live presence, ordinal assignment, peaks, and population transitions.

A population scope is either the entire network or one normalized logical gamemode:

```java
DataRegistryApi dataRegistry = apiProvider.getDataRegistry();
if (!dataRegistry.supports(DataRegistryFeature.POPULATION)) {
    return;
}

PopulationData population = dataRegistry.population();
population.findNetworkSnapshot().thenAccept(snapshotOpt -> snapshotOpt.ifPresent(snapshot -> {
    long uniquePlayers = snapshot.uniquePlayerCount();
    long onlineNow = snapshot.currentOnline();
    long allTimePeak = snapshot.onlinePeak();
}));

population.findSnapshot(PopulationScope.gamemode("survival"))
        .thenAccept(snapshotOpt -> snapshotOpt.ifPresent(snapshot -> {
            long localUniquePlayers = snapshot.uniquePlayerCount();
        }));
```

Population owns these canonical values:

- network unique-player count
- logical-gamemode unique-player count
- current network online count
- current logical-gamemode online count
- network and logical-gamemode online peaks
- one durable network ordinal per player
- one durable ordinal per player per logical gamemode
- durable first-join correlation to the creating network session and gamemode visit
- a cursor-based transition journal for downstream event-style consumers

Use membership reads when a feature needs the player's stable number:

```java
population.findMembership(PlayerLookup.uuid(uuid), PopulationScope.gamemode("survival"))
        .thenAccept(membershipOpt -> membershipOpt.ifPresent(membership -> {
            long playerNumber = membership.ordinal();
        }));
```

Live ordinals are allocated atomically inside the same authoritative lifecycle transaction as status/session state.
They are `RECORDED_EXACT`. When Population is introduced to a database that already contains DataRegistry history,
existing network and gamemode memberships are reconstructed deterministically from the strongest canonical history
available and are marked `BACKFILLED_DETERMINISTIC` instead of pretending those historic numbers were recorded live.

`PopulationSnapshot.membershipBaselineQuality()` and `peakBaselineQuality()` describe historical completeness. A new
empty DataRegistry population starts `VERIFIED`. A database that already contains pre-Population history starts
`TRACKED_ONLY` until an administrator explicitly verifies/seeds the historic baseline. Current/live state after
Population starts is still maintained exactly.

For join-triggered features on Paper, use the durable join context instead of comparing timestamps or querying a
count after the fact:

```java
population.findJoinContext(player.getUniqueId(), serverName)
        .thenAccept(contextOpt -> contextOpt.ifPresent(context -> {
            if (context.gamemodeFirstJoinThisVisit()) {
                long localNumber = context.gamemodeMembership().orElseThrow().ordinal();
            }
            if (context.networkFirstJoinThisSession()) {
                long networkNumber = context.networkMembership().ordinal();
            }
        }));
```

The context is valid only for the player's current durable online server/session/visit. This prevents a later query,
reconnect, or backend switch from being mistaken for the original first join.

Downstream milestone-style consumers should poll the transition journal by cursor rather than repeatedly counting
large player tables:

```java
PopulationTransitionQuery query = PopulationTransitionQuery.after(lastProcessedId, 250)
        .withCauses(Set.of(PopulationTransitionCause.LIVE));

population.findTransitions(query).thenAccept(batch -> {
    if (batch.hasRetentionGapAfter(lastProcessedId)) {
        // Consumer cursor is older than retained transition history: resnapshot/reconcile before continuing.
    }
    for (PopulationTransition transition : batch.transitions()) {
        // MEMBERSHIP_ADDED, ONLINE_CHANGED, or ONLINE_PEAK_CHANGED
    }
});
```

Transition retention is configurable with `retention.population-transition-days`. Purging transition rows never
removes memberships, ordinals, unique counts, current online state, or peak state. `PopulationTransitionBatch`
includes the earliest/latest retained IDs so consumers can detect a cursor that fell behind retention.

Population reuses the existing canonical server-to-gamemode resolver. A transfer such as `survival-1` to
`survival-2` therefore leaves the logical survival online count unchanged when both backend names map to `survival`;
a transfer from survival to creative moves one player between those two logical scopes while network online remains
unchanged.

DataRegistry owns the population facts, not feature policy. Reward commands, milestone thresholds, warning rules,
welcome messages, and already-fired milestone claims belong in the consuming feature/plugin rather than in
DataRegistry.

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
# Fast reactor verification: unit tests, Checkstyle, coverage and dependency hygiene.
./mvnw -B -ntp verify

# Adds the MySQL integration suite. It creates the schema from the production Hibernate
# mappings before exercising the public DataRegistry API.
./mvnw -B -ntp -Pintegration-tests verify

# Builds the bundled Paper and Velocity artifacts, then boots each in the real target
# platform with a consumer compiled only against dataregistry-api. This suite also runs
# the fast reactor checks; it does not include the MySQL integration suite.
./mvnw -B -ntp -Pplatform-acceptance verify

# Full local release gate: fast checks, MySQL integration, and Paper/Velocity acceptance.
./mvnw -B -ntp -Pintegration-tests,platform-acceptance verify
```

The integration and platform suites need a reachable Docker daemon. The platform suite additionally needs `curl`,
`jq`, `sha256sum`, `jar`, and exactly Java 25. Java 26 is intentionally rejected because the currently supported
DataProvider/Hibernate runtime is qualified against Java 25. The platform suite downloads the configured Paper and
Velocity runtime builds, checks their SHA-256 values, provisions MySQL 8.4, checks public API
reads and writes, reloads DataProvider configuration, and requires clean DataRegistry and Hikari shutdown. Set
`PLATFORM_ACCEPTANCE_KEEP_WORK_DIRECTORY=true` to retain server logs after a local run.

The tag release workflow runs both profiles against the exact tagged reactor before Maven deployment. This
keeps the fast checks, MySQL schema compatibility, and real bundled-plugin boot checks in the release gate.

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
