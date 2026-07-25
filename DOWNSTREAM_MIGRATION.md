# Migrating downstream features

DataRegistry `1.11.5` exposes a persistence-agnostic API for feature plugins. Downstream plugins must use the
`dataregistry-api` artifact and must not depend on `dataregistry-core` or a platform implementation artifact.

## Maven dependency

Remove the old monolithic DataRegistry dependency and add the API as `provided`:

```xml
<dependency>
  <groupId>nl.hauntedmc.dataregistry</groupId>
  <artifactId>dataregistry-api</artifactId>
  <version>1.11.5</version>
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
