# T-12: Audit/Ledger Partitioning Procedure

**Decision:** `audit_logs` (365-day retention) + `ledger_entries` (unbounded)
move to monthly RANGE partitioning. V45 leaves the guard + template; the real
cutover runs in a maintenance window with `pg_partman`.

**Procedure:**
1. Open a maintenance window; drain write traffic (the outbox poller may pause).
2. Pre-create partitions 3 months forward + 1 month back with
   `CREATE TABLE ... PARTITION OF ... FOR VALUES FROM ... TO ...`.
3. Backfill old data with `pg_partman` before `DETACH`; rebuild indexes
   partition-local (V40 indexes are the template).
4. Switch retention jobs to `DROP PARTITION` (instant, instead of DELETE).
5. Add a partition-drift alarm to the runbook (see prometheus-rules).

**Alternative (rejected):** Continuous DELETE retention — table bloat +
VACUUM load; unsustainable past 1 year of audit data.

## Activation (O-2)

The cutover is scripted (no pg_partman dependency):

- `scripts/audit_partition_cutover.sql` — one-time maintenance-window swap:
  partitioned parent + history/half-year partitions + DEFAULT catch-all,
  backfill, atomic rename, identity restart, index recreation, verification
  queries. Requires Flyway V46 (TIMESTAMPTZ key). Keeps `audit_logs_legacy`
  until post-swap reads are green.
- `scripts/audit_partition_finalize.sql` — second half: row-count guard,
  legacy drop, `_new` → canonical index renames. Run once, after green reads.
- `scripts/ensure_audit_partition.sql` — quarterly idempotent pre-creation of
  the current + next half-year partitions (runbook diary entry). All three
  scripts are executed end-to-end against real PostgreSQL 15 before merge
  (cutover/ensure/finalize + partition routing verified).
- `k8s/audit-partition-cronjob.yaml` (2026-10) schedules that script on the
  first day of every quarter at 03:00 UTC, with `lock_timeout=10s` so a stuck
  DDL never queues audit inserts. The ConfigMap is created from the script so
  the SQL has one source:
  `kubectl create configmap bank-audit-maintenance-sql --from-file=scripts/ensure_audit_partition.sql`.
  The CronJob is operational coverage, not a replacement for the one-time
  cutover: F8 stays partially open until the application is actually running
  on the partitioned parent.
- V67 interaction: the cutover script copies `chain_seq`/`prev_hash`/`row_hash`,
  recreates the append-only + hash-chain triggers on the new parent, re-points
  `audit_chain_state`, and prints `audit_logs_first_broken_chain_seq()`.
  Prefix-only retention is partition-compatible: dropping the oldest
  partition is the same "oldest chain prefix" operation as the DELETE path.
- Drift signal: `BacklogMetricsReporter` exposes
  `audit.default_partition.rows`; `AuditDefaultPartitionNonEmpty` pages when
  rows accumulate in DEFAULT (missed ensure-job). Pre-cutover the gauge reads
  0 and the alert is quiet.

Remaining follow-up (still open): switch `AuditRetentionScheduler` from
cross-partition DELETE to DROP PARTITION (instant) once the oldest live
partition is fully outside retention.
