# Migration Ownership (I-09)

## Current state

All Flyway migrations live in one directory —
`common/src/main/resources/db/migration/V1__…V4x__….sql` — while the JPA
entities they serve live in their bounded contexts (`account`, `transfer`,
…). The `persistence` module carries the name but owns almost no schema.
This is backwards from the module story (BCs own their tables) and makes
independent schema evolution harder than it should be.

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
