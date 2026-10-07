# LOCAL restore-drill rehearsal (NOT staging evidence)
- date_utc: 2026-10-06T21:12:09Z
- git_sha: e51804baef9ca1482f4b89523fdb4c6955475253
- flyway_version: 32
## Baseline vs restored
- users: source=62 restored=62
- accounts: source=12 restored=12
- transfers: source=64 restored=64
- audit_logs: source=14 restored=14
- ledger_entries: source=0 restored=0
- totals_source: USD=2000.00, TRY=455930.00
- totals_restored: TRY=455930.00, USD=2000.00
- totals_match: True (values identical; raw string compare was order-sensitive — compare per-currency rows, as ops/restore-drill.sh does with ORDER BY)
- nonzero_ledger_groups: NONE
- ledger_note: source ledger_entries holds no transfer legs in this env � net-zero check is vacuous here; staging drill must run it against real legs
- row_counts_match: True
- smoke_on_restored_db_exit: 1 (procedural gap found: dev profile disables liveness/readiness groups, so --require-probes 404s; fixed by booting with MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true — see ops/restore-drill.sh note; re-run below passed with exit 0; verified 4/4 PASS with probes enabled: health, liveness, readiness, browser-session)
- achieved_RPO_RTO: N/A for local rehearsal (measured only in staging drill)
- cleanup: rehearsal DB dropped, dump removed
