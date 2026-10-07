# Restore drill record (template — copy per drill, commit the result)

- date_utc:
- operator (role, not personal data):
- staging_target:
- image_digest (`k8s/.image-digest` first line):
- git_sha:
- flyway_version (`SELECT MAX(version) FROM flyway_schema_history`):
- backup_method + destination + backup_timestamp_utc:
- baseline: users / accounts / transfers / audit_logs / ledger_entries counts + per-currency totals (see `ops/restore-drill.sh` output):
- ledger reconcile: nonzero `transaction_ref` groups (must be none — paste query output):
- totals reconcile: match / mismatch (details):
- smoke check: `ops/health_smoke.py --require-probes --json` output:
- achieved data-loss interval vs RPO ≤ 15 min:
- achieved time-to-recovery vs RTO ≤ 1 h:
- gaps filed as issues (ids):
