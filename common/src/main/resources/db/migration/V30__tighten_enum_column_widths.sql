-- V30__tighten_enum_column_widths.sql
-- V1 declared every code column as VARCHAR(255); the CHECK constraints added
-- since (V3/V6/V8/V28) prove only a handful of short values are ever valid.
-- Narrow the columns to their real domains: smaller rows, clearer schema, and
-- the database rejects over-long codes even where a CHECK lists values.
-- Full PostgreSQL ENUM types are the natural next step but require a Java-side
-- String->enum migration in the JPA entities; see docs/decisions/enum-types.md.
-- Safe online on small tables; assess ACCESS EXCLUSIVE lock time in staging
-- before rolling against a large transfers table.

ALTER TABLE accounts ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE accounts ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE transfers ALTER COLUMN status TYPE VARCHAR(10);
ALTER TABLE transfers ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20);

ALTER TABLE ledger_entries ALTER COLUMN direction TYPE VARCHAR(6);

ALTER TABLE audit_logs ALTER COLUMN action TYPE VARCHAR(30);
