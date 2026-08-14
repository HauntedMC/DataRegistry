# Migrating downstream features

## 1.14.0 playtime lifecycle upgrade

With the default `orm.schema-mode: update`, Hibernate adds the `tracked_gamemodes` catalog and the
`last_joined_at`, `last_exited_at`, `last_logout_at`, and `lifecycle_history_complete` columns. DataRegistry then
runs an idempotent, batches-of-500 backfill before publishing readiness. It derives only facts still present in raw
segments and marks a row incomplete when retained segment count differs from its durable segment count.

Install DataRegistry on Velocity first so it can reconcile the central gamemode policy from the authoritative playtime
configuration. Paper DataRegistry instances are read-only bridges and must follow after that startup succeeds.

Sites using `validate` or `none` must apply equivalent DDL before deployment. The example below is for
MySQL-compatible databases; adapt it for your database dialect and existing migration tool:

```sql
CREATE TABLE tracked_gamemodes (
  gamemode_key VARCHAR(64) NOT NULL,
  counted_towards_network_total BOOLEAN NOT NULL,
  first_observed_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL,
  PRIMARY KEY (gamemode_key)
);

ALTER TABLE player_playtime
  ADD COLUMN last_joined_at TIMESTAMP NULL,
  ADD COLUMN last_exited_at TIMESTAMP NULL,
  ADD COLUMN last_logout_at TIMESTAMP NULL,
  ADD COLUMN lifecycle_history_complete BOOLEAN NULL;

CREATE INDEX idx_ppt_gamemode_last_joined
  ON player_playtime (gamemode_key, last_joined_at);
```

Do not manufacture missing lifecycle timestamps during a manual migration. The startup backfill intentionally leaves
unknown values null and exposes `lifecycleHistoryComplete=false` to consumers.

DataRegistry `1.14.0` exposes a persistence-agnostic API for feature plugins. Downstream plugins must use the
`dataregistry-api` artifact and must not depend on `dataregistry-core` or a platform implementation artifact.

## Maven dependency

Remove the old monolithic DataRegistry dependency and add the API as `provided`:

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

## Replace persistence coupling

Do not map, query, create, update, or merge `PlayerEntity` (or any other DataRegistry entity) in a feature ORM
model. Store the stable scalar `playerId` in feature-owned tables instead. Resolve identities and player metadata
through `DataRegistryApi#players()`.

Use `whenReady(UUID)` during player lifecycle handling, when the canonical player row may need to be created or
updated. Use asynchronous lookup methods such as `findIdentity`, `findPlayerId`, and `findProfileByIdentifier`
elsewhere. Treat returned `CompletionStage` values as asynchronous: do not block a Paper or Velocity event thread,
and schedule any platform API work in completion callbacks back onto the appropriate platform thread.

## Obtain the API

Platform plugins expose the `DataRegistryApiProvider` capability. Retrieve `DataRegistryApi` from the installed
platform plugin, then program only against the public `DataRegistryApi`, `PlayerData`, and immutable API value types.
The public API intentionally has no path to a DataProvider handle, ORM context, repository, entity, or lifecycle
writer.

For optional integrations, check `DataRegistryApi#isReady()` and the relevant `supports(DataRegistryFeature)` method
before relying on an optional built-in domain. A disabled feature returns no persisted data; feature plugins should
handle that state without issuing raw database queries.

## Share feature-owned data through services

Keep feature tables and business rules in their owning plugin. To expose a narrow optional integration, register a
stable interface with `DataRegistryApi#featureServices()` and close its `FeatureServiceHandle` during plugin disable.
Consumers should use `find` for optional dependencies and `require` only when the feature cannot operate without the
service.
