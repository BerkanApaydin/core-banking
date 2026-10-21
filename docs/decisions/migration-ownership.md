# Migration Ownership (I-09)

## Current state

All Flyway migrations live in one resources-only module
(`migrations/src/main/resources/db/migration/V1__…V60__.sql`), while the
JPA entities they serve live in their bounded contexts (`account`,
`transfer`, …). `common` no longer packages schema resources. The classpath
location (`classpath:db/migration`) and every file name are unchanged, so
Flyway history and checksums are unaffected by the move.

The `persistence` module carries the name but owns almost no schema. This is
still backwards from the module story (BCs own their tables) and makes
independent schema evolution harder than it should be; the per-BC split below
remains the long-term goal.

## Migration module (R-C07, done)

`app` consumes the module at compile scope; modules with Testcontainers
integration tests add it at test scope. The module stays Java-free and keeps
the resources at `classpath:db/migration`.

## Convention (effective immediately, V44+)

Every new migration file must start with an owner header:

```sql
-- Owner: transfer
-- Reason: covering index for keyset report pagination
```

`Owner` is the bounded-context module that owns the tables touched
(`account`, `transfer`, `user`, `audit`, `infra` for outbox/idempotency,
`shared` only for genuinely cross-cutting changes). Reviewers reject
owner-less migrations; the header is what a future split script groups by.

## Heavy-statement convention (HIGH-4)

The `bank_user` role carries `statement_timeout = '30s'`
(`docker-init/init-db.sql`). Any migration with a full-table `UPDATE`,
`VALIDATE CONSTRAINT`, column-type rewrite or large index build must open
with `SET LOCAL statement_timeout = '10min';` (Flyway runs each migration in
one transaction, so `SET LOCAL` applies). `CREATE INDEX CONCURRENTLY` is
illegal inside Flyway's transaction — large indexes belong in
`scripts/create_index_concurrently.sql` as an operator step instead.
`scripts/check_migration_timeouts.py` (CI + `test-all.sh`) fails any heavy
migration without the lift, and now classifies statements after stripping
comments/literals so a commented-out `UPDATE` cannot mask a real one. It also
covers `ADD COLUMN ... DEFAULT`, `GENERATED ... STORED`, `SET NOT NULL`,
`ADD CONSTRAINT`, `SET TABLESPACE`, `VACUUM FULL`, `CLUSTER` and `REINDEX`.
Both `SET LOCAL` statements must appear before the first heavy/rewrite
statement, not merely somewhere in the file. Table rewrites additionally need
`SET LOCAL lock_timeout = '30s';` (fail fast on lock contention instead of
wedging the deploy); migrations applied before those rules (V3, V6, V7, V8,
V9, V10, V12, V14, V24, V28, V30, V32, V36, V38, V39, V46, V53, V54, V56,
V60) are grandfathered in the script — applied checksums are frozen and must
not be edited.

## Immutability vs. audit findings (R-O03/R-O04)

Two audit findings propose editing already-merged migrations: add
`lock_timeout` to V28, and squash the V46/V48 projection churn. Both
observations are correct, but the proposed edits are rejected. V28, V32 and
V46 are in `GRANDFATHERED` because Flyway `validate-on-migrate` freezes their
checksums; editing them breaks every existing database and provides no runtime
benefit (fresh databases run the same statements either way). The accepted
debt is recorded here and covered by the forward-looking controls:

- `SET LOCAL lock_timeout = '30s'` is required before every table rewrite in
  a non-grandfathered migration (`scripts/check_migration_timeouts.py`);
- the Flyway Job sets `lock_timeout=30s` as a session default in its JDBC URL,
  so even a grandfathered rewrite (V28 on a fresh database) fails fast instead
  of wedging the rollout;
- large index builds stay in `scripts/create_index_concurrently.sql`;
- new work follows the net-zero rule below.

**Net-zero rule (R-O04):** within one unreleased batch, never create a
projection/relation in one migration and drop it in a later migration of the
same batch. If the change is already merged, leave it immutable and remove
the dead object in a forward migration (V48 follows this). Squashing is only
allowed before merge, never after a checksum has been applied.

## Long-term split plan (not yet executed — deliberate)

1. Group the existing files by owner header (backfill headers in one
   docs-only pass; no file moves, checksums must not change).
2. Introduce per-BC Flyway locations
   (`db/migration/account`, `db/migration/transfer`, …) with one
   `Flyway` bean per location sharing the same history table, ordered by
   the existing version sequence.
3. Move (or delete, if empty) the `persistence` module: either it becomes
   the home of `infra`-owned tables (outbox, idempotency) or it is
   removed and those tables move under `infrastructure` resources.

Step 2–3 change migration checksums/locations and therefore need a
`baseline-on-migrate` review plus a staging soak
(`scripts/migration_soak.sh`) — that is why this decision ships the
convention now and the move later.
