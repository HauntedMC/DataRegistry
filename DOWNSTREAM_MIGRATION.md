# Migrating downstream features

## DataRegistry 1.14.0

DataRegistry `1.14.0` is a deliberate API/domain release. `DataRegistryApi#population()` is now part of the required
public contract; there is no compatibility shim for custom implementations compiled against an older API. Update all
downstream consumers, test doubles, and platform bundles together.

The public dependency remains the persistence-agnostic `dataregistry-api` artifact. Downstream plugins must not depend
on `dataregistry-core` or a platform implementation artifact.

### Population database upgrade

With the default `orm.schema-mode: update`, Hibernate creates the Population tables automatically:

- `population_scope_state` for canonical unique counts, current online counts, online peaks, and baseline metadata
- `player_population_membership` for immutable network/gamemode ordinals and first-join correlation
- `population_transition` for the durable cursor-based transition feed

Velocity, as lifecycle authority, then performs the idempotent Population backfill before the API becomes ready.
Existing network membership is ordered by the strongest canonical first-seen evidence available. Existing logical
gamemode membership is reconstructed from an already-existing canonical `player_playtime` table when that historical
evidence is present. Population discovers that table during migration even when the PLAYTIME runtime feature is
disabled; it does not register or create Playtime storage merely to perform the discovery. Backfilled ordinals are
explicitly marked `BACKFILLED_DETERMINISTIC`; new live ordinals are `RECORDED_EXACT`.

An existing database starts with `TRACKED_ONLY` historical baseline quality because DataRegistry cannot invent player
or peak history that predates its stored evidence. A new empty database starts `VERIFIED`. This does not make current
live state approximate: after Population starts, lifecycle-driven counts and ordinals are maintained atomically.

Install/start the new DataRegistry on Velocity first so the authoritative lifecycle writer performs migration and
presence reconciliation. Paper DataRegistry instances remain bridge/read runtimes and should start against the
upgraded schema afterwards.

Sites using `orm.schema-mode: validate` or `none` must create equivalent Population tables/indexes with their normal
migration tooling before deploying 1.14.0. Do not manually allocate historical ordinals independently on multiple
nodes; let the single Velocity lifecycle-authority migration populate them or reproduce its deterministic ordering
exactly in a controlled migration.

### Configuration upgrade

Current DataRegistry releases reconcile an existing `config.yml` with the packaged template on startup. Missing keys
are inserted while operator-provided values and YAML comments are preserved. Invalid configured values warn and fall
back to runtime defaults. Unknown keys are preserved but ignored and are reported by path so configuration typos are
visible instead of silently taking effect as defaults.

A fresh 1.14 template enables `features.population`. For an older config that does not contain the new Population key,
DataRegistry preserves an explicit opt-out of any required prerequisite (`online-status`, `sessions`, or
`session-visits`) by inserting Population as disabled. If `features.population: true` is explicitly configured, those
required domains are enabled at runtime with a warning when necessary. Omitted
`retention.population-transition-days` defaults to 90 days.

Use the current packaged `dataregistry-core/src/main/resources/config.yml` as the reference when reviewing settings.
New keys are inserted automatically during an upgrade; regenerate the whole file only when you intentionally want a
fresh copy of the current template and its ordering/documentation.

### Existing playtime lifecycle metadata

The existing playtime lifecycle upgrade still uses the `tracked_gamemodes` catalog plus the `last_joined_at`,
`last_exited_at`, `last_logout_at`, and `lifecycle_history_complete` columns. Do not manufacture missing lifecycle
timestamps during a manual migration. DataRegistry leaves unknown values null and exposes incomplete history as such.

## Maven dependency

Depend on the API as `provided`:

```xml
<dependency>
  <groupId>nl.hauntedmc.dataregistry</groupId>
  <artifactId>dataregistry-api</artifactId>
  <version>1.14.0</version>
  <scope>provided</scope>
</dependency>
```

The server supplies the API through the installed bundled Paper or Velocity plugin. Do not shade the API, core, or
platform artifacts into a feature plugin.

## Required API implementation change

`DataRegistryApi` now requires both domain facades:

```java
public interface DataRegistryApi {
    PlayerData players();
    PopulationData population();
    // ... capability/service methods
}
```

Any custom `DataRegistryApi` implementation must implement `population()`. The testkit follows the same contract:
`FakeDataRegistryApi` requires an explicit `PopulationData`, normally `FakePopulationData` for feature tests.

Consumers that can operate without Population should still check the feature capability before using that facade:

```java
if (dataRegistry.supports(DataRegistryFeature.POPULATION)) {
    PopulationData population = dataRegistry.population();
}
```

The method itself is required by the API type; the capability flag describes whether the runtime has that built-in
domain enabled.

## Replace persistence coupling

Do not map, query, create, update, or merge `PlayerEntity` (or any other DataRegistry entity) in a feature ORM
model. Store the stable scalar `playerId` in feature-owned tables instead. Resolve identities and player metadata
through `DataRegistryApi#players()` and canonical population facts through `DataRegistryApi#population()`.

Use `whenReady(UUID)` during player lifecycle handling, when the canonical player row may need to be created or
updated. Use asynchronous lookup methods such as `findIdentity`, `findPlayerId`, and `findProfileByIdentifier`
elsewhere. Treat returned `CompletionStage` values as asynchronous: do not block a Paper or Velocity event thread,
and schedule any platform API work in completion callbacks back onto the appropriate platform thread.

For first-join behavior, do not derive a player number from `COUNT(*) + 1` or infer first join from timestamps.
Population provides durable membership ordinals and `findJoinContext(UUID, serverName)`, which correlates the current
session/visit with the membership that originally created the ordinal.

For milestone/event consumers, use the cursor-based Population transition feed rather than periodically recounting
large player tables. On first enable, persist the value returned by `PopulationData#latestTransitionId()` as the
consumer cursor so historical transitions are not replayed. Afterwards,
`PopulationTransitionBatch#hasRetentionGapAfter(cursor)` tells a consumer when its persisted cursor fell behind
configured transition retention and it must resnapshot/reconcile before continuing.

Retention deletes only an expired contiguous prefix of transition history and deliberately keeps the newest retained
transition as a high-water anchor. Memberships, ordinals, unique counts, current-online state, and peaks are never
removed by transition retention.

## Obtain the API

Platform plugins expose the `DataRegistryApiProvider` capability. Retrieve `DataRegistryApi` from the installed
platform plugin, then program only against the public `DataRegistryApi`, `PlayerData`, `PopulationData`, and immutable
API value types. The public API intentionally has no path to a DataProvider handle, ORM context, repository, entity,
or lifecycle writer.

For optional integrations, check `DataRegistryApi#isReady()` and the relevant `supports(DataRegistryFeature)` method
before relying on a toggleable built-in domain. Feature plugins should handle a disabled capability without issuing
raw database queries.

## Share feature-owned data through services

Keep feature tables and business rules in their owning plugin. Population owns canonical facts only; it does not own
welcome rewards, milestone thresholds, warning rules, or already-fired claims. To expose a narrow optional feature
integration, register a stable interface with `DataRegistryApi#featureServices()` and close its
`FeatureServiceHandle` during plugin disable. Consumers should use `find` for optional dependencies and `require` only
when the feature cannot operate without the service.
