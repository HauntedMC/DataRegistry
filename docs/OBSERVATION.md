# Operation observation

DataRegistry exposes an optional, vendor-neutral observation SPI for infrastructure integrations that need to measure or trace meaningful DataRegistry operations without coupling DataRegistry to OpenTelemetry, HauntedObservability, or another telemetry implementation.

## Attach an observer

Resolve the platform plugin through `DataRegistryApiProvider`, register an observer on the active runtime capability, and retain the returned registration for the consumer lifecycle:

```java
DataRegistryApiProvider provider = /* DataRegistry platform plugin */;
DataRegistryObservationRegistration registration = provider
        .getDataRegistryInstrumentation()
        .registerObserver(observer);
```

Close the registration when the consuming integration shuts down. Registrations belong to one active DataRegistry runtime; they are not installed globally and do not affect another runtime instance. The compatibility default on `DataRegistryApiProvider` is a no-op, while the bundled Paper and Velocity providers resolve the instrumentation capability implemented by their active core runtime.

The no-observer path is intentionally cheap. DataRegistry avoids creating operation contexts and, where relevant, avoids attaching completion callbacks when no observer is registered.

## Public SPI

The observation contract consists of:

- `DataRegistryInstrumentation`, which registers runtime-local observers;
- `DataRegistryObserver`, which starts an observation for a stable operation context;
- `DataRegistryObservation`, which can activate context and receives exactly one terminal callback;
- `DataRegistryObservationScope`, which lets an adapter activate implementation-specific context around DataRegistry work without DataRegistry knowing the context technology;
- `DataRegistryObservationRegistration`, which detaches one observer;
- `DataRegistryOperationContext`, which contains only the stable operation name;
- `DataRegistryOperationOutcome`, which provides a bounded terminal classification.

Observer callbacks must be thread-safe and non-blocking. DataRegistry isolates runtime exceptions raised by observer start, scope, completion, and scope-close callbacks so instrumentation cannot change the result of the underlying DataRegistry operation.

## Metadata and cardinality boundary

`DataRegistryOperationContext` intentionally contains exactly one payload-free value: a DataRegistry-owned, bounded operation name such as `player.lifecycle.login`, `player.readiness.wait`, or `population.reconcile`.

It deliberately does **not** expose:

- database player IDs, UUIDs, usernames, IP addresses, virtual hosts, or other player data;
- service names, service-instance IDs, target hosts, ports, or probe details;
- DataProvider connection identifiers;
- SQL, HQL, query parameters, table names, keys, cursors, or arbitrary caller input;
- command arguments or other payload data.

Operation names are suitable for low-cardinality metrics. The supplied `Throwable`, when present, is diagnostic context rather than metric metadata: exception messages may contain backend or application details and must never be copied into metric labels. A telemetry adapter is responsible for applying its own trace/log privacy policy to failures.

## Completion and context semantics

Synchronous operations complete before returning or throwing. Asynchronous query and readiness observations complete when their returned future actually completes, so timing includes the real wait/backend work rather than only submission.

`DataRegistryObservation#openScope()` exists specifically for context propagation across execution boundaries. The query executor activates the observation on the virtual thread that performs the database work. This allows a future HauntedObservability adapter to make a trace/span current without DataRegistry importing OpenTelemetry APIs.

Lifecycle writes use one logical observation across retry attempts. `completed(...)` reports the final bounded outcome and the number of attempts made. Duplicate idempotent lifecycle events are classified as `DUPLICATE`; exhausted retryable failures and non-retryable failures remain distinguishable.

## Current operation coverage

The initial contract observes meaningful DataRegistry semantic boundaries rather than every repository call:

- runtime initialization and shutdown: `registry.initialize`, `registry.shutdown`;
- asynchronous player and profile/query operations through the public DataRegistry query executor, using their existing bounded `player.*`, `playtime.*`, and population query operation names;
- player lifecycle readiness: `player.readiness.wait`;
- authoritative player lifecycle persistence: `player.lifecycle.login`, `player.lifecycle.transfer`, `player.lifecycle.disconnect`;
- population maintenance: `population.migrate`, `population.reconcile`;
- live playtime policy application: `playtime.policy.reconcile`;
- service-registry writes and retention: instance refresh/stop, probe recording, and bounded purge operations.

Internal repository helpers, Hibernate calls, individual row mutations, service-registry read helpers, diagnostics, and other low-level storage mechanics are intentionally not separate DataRegistry observations in this first contract.

## DataRegistry versus DataProvider observations

The two SPIs describe different layers and are intentionally complementary:

```text
player lifecycle / readiness / registry operation   <- DataRegistry observation
└── ORM transaction / backend data operation        <- DataProvider observation
```

DataRegistry answers **which HauntedMC semantic operation is waiting or failing**. DataProvider answers **which storage operation underneath it is slow or failing**. HauntedObservability should preserve that parent/child relationship rather than creating duplicate DataRegistry spans for every SQL/Redis/ORM action.

DataRegistry itself has no OpenTelemetry or HauntedObservability dependency. The later `hauntedobservability-dataregistry` module will implement this neutral SPI while DataRegistry remains independently usable with the built-in no-op path.
