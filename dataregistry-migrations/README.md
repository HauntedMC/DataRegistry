# DataRegistry migrations

`db/migration/V1__baseline.sql` is the MySQL 8 baseline matching the core JPA mappings. Apply it with the deployment
migration runner before starting DataRegistry with `orm.schema-mode: validate` (the production default).

This artifact provides versioned SQL resources only; it does not include or invoke a migration runner. Deployment
automation must apply the scripts exactly once and record migration state before either platform plugin starts.
New schema changes must be added as a new ordered `V<version>__<description>.sql` file. Never edit an applied
migration, and never rely on Hibernate schema mutation in production.
