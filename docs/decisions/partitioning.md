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
