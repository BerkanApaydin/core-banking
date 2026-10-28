# AUDIT-2: Tamper-evident audit hash chain

**Status:** partially implemented (2026-10). F1 is reduced, not closed.

**Decision:** every `audit_logs` row is linked to its predecessor by a SHA-256
hash computed inside PostgreSQL. A nightly verifier recomputes the chain and
publishes `audit.chain.broken`; retention deletes only the oldest chain prefix.
This detects direct `UPDATE`/`DELETE`/suffix-removal through the application
role without requiring a WORM store, but it is **not** a substitute for
separated database roles, an external anchor, or KMS/HSM key custody.

## Why

The V62 append-only trigger stops accidental application mutations, but an
actor with DDL rights can disable it, and a `DELETE` from the head leaves no
trace in the rows that remain. The review (F1) asked for tamper evidence that
survives a database-only compromise. A hash chain gives the cheapest useful
step: any rewrite or middle deletion breaks a recomputable link, and the
singleton chain head catches a removed suffix.

## Shape

- `audit_logs` gains `chain_seq BIGINT`, `prev_hash VARCHAR(64)`,
  `row_hash VARCHAR(64)` (`V67__audit_hash_chain.sql`).
- `trg_audit_logs_chain` (BEFORE INSERT) calls `audit_logs_assign_chain()`:
  - takes `SELECT ... FOR UPDATE` on the singleton `audit_chain_state` row;
  - reads `last_seq`/`last_hash`, assigns `chain_seq = last_seq + 1`;
  - computes `row_hash = sha256(canonical fields || prev_hash)`;
  - updates the state row in the same transaction.
- Canonical fields are `username`, `action`, `details`, `timestamp`,
  `actor_user_id`; values are coalesced and joined with `E'\x1f'`, and the
  timestamp is formatted in UTC with microsecond precision. Changing either
  the row or the separator/precision changes the hash.
- `audit_logs_first_broken_chain_seq()` returns the first broken `chain_seq`,
  or `NULL`:
  1. compares `audit_chain_state.last_seq/last_hash` with the newest row
     (catches truncated suffixes and a rewritten head);
  2. walks the chain with `lag(row_hash)` and recomputes every row hash
     (catches rewrites and middle deletions);
  3. skips the `prev_hash` check for the oldest retained row, because prefix
     retention intentionally removes its predecessor.
- `delete_audit_logs_before(timestamp, batch)` now finds the first retained
  row (`MIN(chain_seq) WHERE timestamp >= cutoff`) and deletes only rows with
  a smaller `chain_seq`. A backdated row inserted behind newer rows is
  therefore retained until every newer row has also fallen outside the cutoff
  (or the whole retained chain ages out), instead of punching a hole in the
  chain. This delays such rows; it does not lose the prefix guarantee.
- `AuditChainVerificationScheduler` runs daily at 03:30
  (`app.audit.chain-verification-cron`, enabled by default) and publishes:
  - `audit.chain.broken` (0/1; page when 1);
  - `audit.chain.last_verified_epoch_seconds` (staleness alert after 48h).

## What it detects

- direct `UPDATE` of a retained row (details, action, username, timestamp,
  actor, chain fields);
- deletion of a row from the middle of the retained chain;
- deletion of the newest row(s) while older rows remain (state head mismatch).

## What it does not detect

- deletion of the entire `audit_logs` table: the verifier returns `NULL` for
  an empty table even when `audit_chain_state` still points at the old head;
  an external anchor is required for this case;
- an actor who rewrites the whole chain **and** `audit_chain_state`
  consistently (database owner, superuser, `BYPASSRLS` role, or someone with
  both table and function DDL);
- a full wipe of `audit_logs` + `audit_chain_state`;
- privilege escalation through the application role (the application still
  holds INSERT and the retention DELETE grant; F2 remains open);
- a compromised KMS/signing key (there is no signature/key yet; the hash is
  unkeyed and recomputable by anyone).

**F1 remains open** for those cases: separate roles (`audit_writer` with
INSERT only, retention role with a narrow `DELETE` path, no DDL), REVOKE of
direct table DML from the app role, and an external anchor (periodic export of
`(last_seq, last_hash)` to object-lock storage or a KMS-backed signature).
Until then this is evidence against ordinary application-path tampering and
accidental operator mistakes, not against a determined database administrator.

## Operational rules

- **Do not "repair" a broken chain in place.** Snapshot the database, preserve
  `audit_logs` + `audit_chain_state`, and treat the alert as a security
  incident. Rebuilding hashes destroys the evidence.
- Run the verifier manually with:
  `SELECT audit_logs_first_broken_chain_seq();`
  `NULL` means healthy at that instant; a number is the first suspect
  `chain_seq`.
- Retention is prefix-only by design. Backdated rows behind newer rows wait
  until every newer row also expires; this is expected, not data loss.
- A partition cutover must copy `chain_seq`/`prev_hash`/`row_hash`, recreate
  the append-only + chain triggers on the new parent, and reset the state row;
  `scripts/audit_partition_cutover.sql` does all three and prints the verifier
  result.
- The quarterly `bank-audit-partition-maintenance` CronJob keeps partitions
  ahead; see `docs/decisions/partitioning.md`.

## Rollout

V67 adds nullable columns, backfills every existing row in `id` order, then
sets the columns `NOT NULL`. It holds an `ACCESS EXCLUSIVE` lock on
`audit_logs` and temporarily disables the V62 append-only trigger during the
backfill, so it must run in the migration window with application writes
drained (the Flyway Job already precedes the Deployment). The backfill is one
`UPDATE` per row: on a large table, budget the maintenance window and watch
`statement_timeout` (V67 sets 10 minutes) before applying in production.

## Cost

The chain serializes audit inserts on one state row. That is deliberate: the
hash must be computed against the latest committed head, and a gap-free
sequence requires a single writer. Audit volume is bounded by money/auth
events, not by reads; if the write-heavy path ever makes this row hot, the
next step is batching audit events per transaction or moving the anchor to an
append-only log — not weakening the chain. Measure before optimizing.

## Verification

- `AuditChainVerificationAdapterTest` (unit) covers healthy/broken mapping.
- `AuditChainVerificationSchedulerTest` covers the two gauges; a failed
  verification leaves the last-success timestamp unchanged, so the 48h
  staleness alert (not a healthy 0) covers the gap.
- `AuditChainIntegrityIntegrationTest` covers chained inserts, rewrite
  detection, middle deletion, head deletion, and prefix-only retention against
  real PostgreSQL (V67 runs through Flyway in Testcontainers).
