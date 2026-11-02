# Rinda backend

Spring Boot 3 REST API on Java 17. The root README has the setup, run and test commands.

- `src/main/resources/db/migration`: the schema, as Flyway migrations.
- `src/main/resources/db/demo`: demo data, loaded only by the `dev` profile.
- `thresholds` table: every value that affects a decision. All are `TO_CONFIRM` until the team confirms them.

Plot polygons are stored by MySQL's own spatial functions. Hibernate's schema validation is off because Flyway owns the schema.
