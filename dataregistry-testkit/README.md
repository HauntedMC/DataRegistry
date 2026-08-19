# DataRegistry Testkit

`dataregistry-testkit` provides lightweight in-memory collaborators for downstream feature and plugin tests. It depends
on `dataregistry-api`, not DataRegistry core persistence, so consumer tests can exercise API contracts without starting
Hibernate, MySQL, Paper, or Velocity.

## Quick start

Add the testkit with test scope using the same version as `dataregistry-api`:

```xml
<dependency>
    <groupId>nl.hauntedmc.dataregistry</groupId>
    <artifactId>dataregistry-testkit</artifactId>
    <version>${dataregistry.version}</version>
    <scope>test</scope>
</dependency>
```

Create a registry with only the domains the feature expects:

```java
FakeDataRegistryApi registry = FakeDataRegistryApi.builder()
        .enable(DataRegistryFeature.LANGUAGE, DataRegistryFeature.PLAYTIME)
        .build();

FakePlayerData players = (FakePlayerData) registry.players();
PlayerIdentity remy = PlayerFixtures.identity(1L, "Remy");
players.putActiveIdentity(remy)
        .putLanguage(new PlayerLanguageSettings(1L, "AUTO", "NL"));
```

The builder supplies `FakePlayerData`, `FakePopulationData`, and `FakeFeatureServiceDirectory` automatically unless a
custom collaborator is provided. Use `enableAll()` for broad integration-style tests and chain `disable(...)` when only
a few unavailable domains need to be modeled; focused tests should generally continue to enable only what they use.

## Player data

`FakePlayerData` implements the complete `PlayerData` and `PlayerDirectory` contracts. It supports identity lookup,
active identities, username-prefix paging, language and nickname writes, connection/IP queries, name history, online
state, activity, playtime, gamemode statistics, leaderboards, and profile projections.

Configure state fluently:

```java
players.putIdentity(PlayerFixtures.identity(2L, "Alice"))
        .putNickname(2L, "Ali")
        .putOnlineStatus(new PlayerOnlineSnapshot(2L, true, "survival-1", "lobby"));

Optional<PlayerProfile> profile = players.findProfile(2L, 5)
        .toCompletableFuture()
        .join();
```

Feature-aware reads mirror disabled-domain behavior: construct the fake with the feature set the test should expose.
Writes that require a disabled feature fail fast so tests do not accidentally depend on unavailable behavior.

## Population

`FakePopulationData` stores snapshots, memberships, join contexts, transitions, and a configurable server-to-gamemode
resolver. Individual entries can be removed, transition history can be cleared, or the whole fake can be reset with
`clear()` between scenarios.

```java
FakePopulationData population = (FakePopulationData) registry.population();
population.putSnapshot(networkSnapshot)
        .putMembership(PlayerLookup.playerId(1L), membership);
```

## Feature services

`FakeFeatureServiceDirectory` implements the same ownership rules as the runtime catalog. The same owner may replace its
service, another owner cannot take over an already claimed API type, and closing an old handle never removes a newer
replacement.

```java
FeatureServiceHandle handle = registry.featureServices().register(
        "ServerFeatures",
        "Vanish",
        VanishApi.class,
        vanishApi
);

VanishApi resolved = registry.featureServices().require(VanishApi.class);
handle.close();
```

## Async edge cases

`FailureSimulation` provides deterministic helpers for exceptional, cancelled, and intentionally incomplete stages:

```java
CompletionStage<Result> failed = FailureSimulation.failedStage(new IOException("database unavailable"));
CompletableFuture<Result> cancelled = FailureSimulation.cancelledFuture();
CompletableFuture<Result> pending = FailureSimulation.neverCompletingFuture();
```

Use these to verify timeout, cancellation, fallback, and error-reporting behavior without sleeping or adding timing
flakiness to tests.
