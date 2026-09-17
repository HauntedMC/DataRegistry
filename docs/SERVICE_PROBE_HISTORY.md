# Service probe history

DataRegistry separates **current probe freshness** from **durable probe history** without introducing a second state table.

## Write model

Velocity still probes every configured backend at `service-registry.probe-interval-seconds`. Every observation updates the logical service's `last_seen_at`, but probe-history persistence depends on the result:

- `DOWN` and `TIMEOUT` observations are always appended. Failure history therefore keeps the full probe cadence for incident analysis.
- The first `UP` observation for a service/observer is appended.
- The first `UP` after a failure is appended, preserving the recovery transition.
- During uninterrupted healthy operation, the newest `UP` row for that exact observer is refreshed in place. Its latency, endpoint metadata and `checked_at` remain current, so effective-health freshness is unchanged.
- A new healthy history row is periodically appended even without a state transition. The archival interval is derived from the active probe cadence: 20 probe intervals, clamped to 5–30 minutes.

Compaction is keyed by `(service kind, service name, observer instance)`. Each proxy therefore owns its own rolling healthy observation and archival cadence; one proxy never rewrites another proxy's current probe row.

With the default 15-second probe cadence, uninterrupted healthy history is archived every 5 minutes. For eight backends this reduces steady-state probe-history inserts from roughly 46,080 rows/day/proxy to roughly 2,304 rows/day/proxy while retaining a fresh health timestamp every 15 seconds.

## Retention

`service-registry.probe-retention-hours` still controls the history cutoff. Retention now fixes the cutoff once and drains **all rows older than that cutoff**. Each database transaction deletes at most `retention.purge-batch-size` rows, so transaction size remains bounded while cleanup throughput is no longer capped at one batch per maintenance interval.

New probes cannot extend an in-progress purge because their `checked_at` values are newer than the fixed cutoff. This makes backlog drainage finite and prevents the previous failure mode where a 500-row purge every 12 hours could never catch a much larger daily write volume.

## Compatibility

No schema migration is required. The existing `service_probe` table remains authoritative for both current probe freshness and historical events. The only mapping change is that `checked_at` is intentionally updateable for the rolling healthy row. Existing probe read APIs and effective-health calculations continue to use the newest persisted observation.
