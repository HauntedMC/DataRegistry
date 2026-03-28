# Contributing to DataRegistry

## Development Standards

- Java: `21`
- Build tool: `Maven 3.8.6+`
- Required quality gate before merge: `mvn verify`
- Coverage gate is enforced by JaCoCo during `verify`.

## Architecture Rules

- Keep platform modules (`platform.bukkit`, `platform.velocity`) thin.
- Put persistence/business behavior in `backend.service`.
- Keep settings validation in `backend.config`.
- Keep repository responsibilities in `api.repository`.
- New built-in data domains must be feature-toggleable through `DataRegistrySettings`.

## Feature Toggle Policy

Data domains can be disabled in `config.yml` under `features`:

- `online-status`
- `connection-info`
- `sessions`
- `name-history`
- `service-registry`

When a feature is disabled:

- Its entities are not registered in ORM bootstrap.
- Services for that domain must no-op and avoid database writes.

Database profile policy:
- Player-facing domains must use the player profile connection (`database.profiles.players.connection-id`).
- Service-facing domains must use the service profile connection (`database.profiles.services.connection-id`).
- Keep domains independent; do not couple optional feature tables into the core identity schema.
- Prefer exposing read-side helper methods via `DataRegistry#newServiceRegistryService()` for cross-feature service discovery instead of duplicating raw queries.

## Adding New Data Domains

1. Create entity class in `api.entities`.
2. Create repository abstraction in `api.repository` if needed.
3. Add a focused service in `backend.service`.
4. Add a feature toggle in `DataRegistryFeature`, `DataRegistrySettings`, and `DataRegistrySettingsLoader`.
5. Wire feature-aware behavior in runtime startup (`DataRegistry` and platform module).
6. Add unit tests for settings parsing, service behavior, and runtime registration.

## Security Guidelines

- Never log raw unbounded user input without sanitization.
- Keep privacy-sensitive fields opt-in (`persist-ip-address`, `persist-virtual-host`).
- Prefer strict input normalization and bounded field lengths.
- Use `SafeConstructor` + constrained `LoaderOptions` for YAML parsing.

## Pull Request Checklist

- [ ] `mvn verify` passes locally.
- [ ] New behavior is documented in `README.md` and/or config comments.
- [ ] New domain changes are feature-toggleable.
- [ ] Failure paths are logged with sanitized values only.
- [ ] Added/updated tests cover success and failure paths.
