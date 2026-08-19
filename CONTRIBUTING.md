# Contributing to DataRegistry

## Development Standards

- Java: `25`
- Build tool: Maven Wrapper (`./mvnw`; Maven 3.8.6+ is enforced by the build)
- Required fast quality gate before merge: `./mvnw -B -ntp verify`
- Required database gate for persistence changes:
  `./mvnw -B -ntp -Pintegration-tests verify`
- Required platform gate for platform, packaging, or public API changes:
  `./mvnw -B -ntp -Pplatform-acceptance verify`
- Run the full release-equivalent verification with
  `./mvnw -B -ntp -Pintegration-tests,platform-acceptance verify`.
- Run `shellcheck update_version.sh dataregistry-platform-acceptance/run-platform-acceptance.sh` after changing either
  repository shell script.
- Docker is required for the integration and platform gates. The platform gate also requires `curl`, `jq`,
  `sha256sum`, and `jar`.
- JaCoCo HTML reports are generated during `verify` under each module's `target/site/jacoco` directory. Failsafe
  reports for the integration suite are generated under `target/failsafe-reports`.

## Architecture Rules

- Keep platform modules (`dataregistry-platform-paper`, `dataregistry-platform-velocity`) thin.
- Put core business behavior in `dataregistry-core` services.
- Keep settings validation in `dataregistry-core` config.
- Keep ORM entities and repositories in `dataregistry-core` persistence.
- New built-in data domains must be feature-toggleable through `DataRegistrySettings`.

## Feature Toggle Policy

Data domains can be disabled in `config.yml` under `features`:

- `online-status`
- `connection-info`
- `activity-summary`
- `sessions`
- `session-visits`
- `playtime`
- `language`
- `nicknames`
- `name-history`
- `population`
- `service-registry`

When a feature is disabled:

- Its entities are not registered in ORM bootstrap.
- Services for that domain must no-op and avoid database writes.

Database profile policy:
- Player-facing domains must use the player profile connection (`database.profiles.players.connection-id`).
- Service-facing domains must use the service profile connection (`database.profiles.services.connection-id`).
- Keep domains independent; do not couple optional feature tables into the core identity schema.
- Use the public `DataRegistryApi#featureServices()` catalog for cross-feature service discovery instead of
  duplicating raw queries. Core implementation wiring remains internal.

## Adding New Data Domains

1. Create an entity class in `dataregistry-core` persistence.
2. Create a repository abstraction in `dataregistry-core` persistence if needed.
3. Add a focused service in `dataregistry-core`.
4. Add a feature toggle in `DataRegistryFeature`, `DataRegistrySettings`, and `DataRegistrySettingsLoader`.
5. Wire feature-aware behavior in runtime startup (`DataRegistry` and platform module).
6. Add unit tests for settings parsing, service behavior, and runtime registration.

## Consumer Contract Tests

Use `dataregistry-testkit` for downstream feature tests instead of mocking the entire DataRegistry API surface. The
module provides `FakeDataRegistryApi`, `FakePlayerData`, `FakePopulationData`, `FakeFeatureServiceDirectory`, fixtures,
and async failure helpers. Its dedicated [README](dataregistry-testkit/README.md) contains setup examples.

Keep fake behavior aligned with the public API contract. When adding a new API operation, extend the corresponding fake
and add a contract test in the same change so downstream tests do not need one-off stubs.

## Release Version Helper

Preview the next version without touching the worktree, creating a commit, or creating a tag:

```bash
./update_version.sh --dry-run patch
```

Use `major`, `minor`, or `patch` without `--dry-run` only from a clean worktree when preparing the actual release. The
script updates the Maven revision and Velocity plugin annotation together, verifies the resulting API version, creates
the local release commit, and creates the matching `vX.Y.Z` tag. The tag workflow independently verifies that the tag
and Maven project version match before publishing anything.

## Security Guidelines

- Never log raw unbounded user input without sanitization.
- Keep privacy-sensitive fields opt-in (`persist-ip-address`, `persist-virtual-host`).
- Prefer strict input normalization and bounded field lengths.
- Use `SafeConstructor` + constrained `LoaderOptions` for YAML parsing.

## Pull Request Checklist

- [ ] The applicable Maven verification profile(s) above pass locally.
- [ ] New behavior is documented in `README.md` and/or config comments.
- [ ] New domain changes are feature-toggleable.
- [ ] Failure paths are logged with sanitized values only.
- [ ] Added/updated tests cover success and failure paths.
