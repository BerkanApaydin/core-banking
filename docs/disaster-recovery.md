# Disaster Recovery (targets + drill)

Owners: platform team (backup/PITR schedule + restore environment),
on-call operator (quarterly drill execution + record). The database
backup itself is a platform capability (pgBackRest / managed snapshot /
`pg_dump` to a separately protected destination); this repository owns
the drill procedure and its evidence.

## Ratified objectives

- **RPO ≤ 15 minutes** for PostgreSQL (WAL archiving / PITR), **RTO ≤ 1 hour**
  for a single-region restore. First staging drill must measure both and
  file any miss as an issue; objectives stand until a drill proves otherwise.
- **Redis (revocations, rate limits, snapshot cache):** RPO n/a by design —
  it is reconstructible state, not a system of record. After any Redis loss:
  1. Expect elevated DB load (snapshot cache cold) and closed rate-limit
     windows; watch pool waiters and `http_server_requests` latency.
  2. Old bearer tokens are valid until expiry (revocations lived in Redis
     when the database/hybrid backend was not in force). With the `hybrid`
     or `database` backend (production requirement), revocations survive in
     PostgreSQL — verify `token_revocations` row counts post-restore.
  3. Do NOT treat an empty Redis as evidence that old tokens are valid.

## Quarterly drill (staging, executable)

Two phases, both fail closed. File each completed record under `docs/drills/`
using `docs/drills/restore-drill-record-template.md` — a drill without a
committed record did not happen.

1. Immediately before the backup, with staging writes quiesced:
   `ops/restore-drill.sh <staging-base-url> docs/drills/<date>-restore.md`
   (DB credentials via `PGHOST/PGUSER/PGPASSWORD/PGDATABASE`). It writes
   `docs/drills/<date>-restore.baseline.json` with row counts, per-currency
   totals, Flyway version and image digest, records the ledger net-zero
   result and runs the staging probe smoke. It refuses to overwrite existing
   evidence; use a new record file for a new drill.
2. Back up with the platform method to a separately protected destination;
   record timestamp, Flyway version (`flyway_schema_history`) and image digest.
3. Restore into an isolated environment with outbound providers disabled.
   Point a compatible app version at it; verify migrations + constraints.
4. Verify the restored database and app:
   `ops/restore-drill.sh --verify <restored-base-url> docs/drills/<date>-restore.md [restored-database-url]`
   (or set `RESTORED_DATABASE_URL` instead of the third argument). It captures
   `<record>.restored.json`, compares Flyway, every tracked table count,
   per-currency totals and the ledger net-zero invariant, runs the probe smoke
   against the restored app, appends the evidence and exits nonzero on any
   drift or failed probe. Neither check is wrapped in `|| true`.
5. Reconcile: ledger legs per `transaction_ref` net to zero; aggregate
   totals match; uncertain operations replayed with ORIGINAL keys only.
   The verifier compares exact decimal strings; retain the record-level
   checks because aggregate totals alone cannot detect every wrong transfer.
6. Record achieved data-loss interval and time-to-recovery against RPO/RTO;
   paste the automated verdict, and file gaps as issues, not tribal knowledge.

Static guards (no database needed): `python3 ops/restore_verify.py --self-test`
and `python3 scripts/check_restore_drill.py`.

Rollback is not a database downgrade: rehearse the previous app version
against the migrated staging schema and choose forward repair when old
binaries cannot run (see docs/operations.md).
