# DataRegistry migrations

`db/migration/V1__baseline.sql` is the MySQL 8.4 baseline matching the core JPA mappings. Apply it with the
deployment migration runner before starting DataRegistry with `orm.schema-mode: validate` (the production default).

This artifact provides versioned SQL resources only; it does not include or invoke a migration runner. Deployment
automation must apply the scripts exactly once and record migration state before either platform plugin starts.
New schema changes must be added as a new ordered `V<version>__<description>.sql` file. Never edit an applied
migration, and never rely on Hibernate schema mutation in production.

Use the repository wrapper to verify migration compatibility locally:

```bash
./mvnw -B -ntp -Pintegration-tests verify
./mvnw -B -ntp -Pplatform-acceptance verify
```

Both commands need Docker. The `integration-tests` profile applies the baseline to a fresh MySQL 8.4 container and
starts the real DataProvider/Hibernate mapping in `validate` mode. The platform-acceptance profile mounts the same
migration into its MySQL container before booting the bundled Paper and Velocity plugins. These checks catch a
migration-to-mapping mismatch, but deployment automation remains the authority for recording and applying production
migrations.
