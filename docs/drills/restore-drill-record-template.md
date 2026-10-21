# Restore drill record (template - copy per drill, commit the result)

The baseline phase (`ops/restore-drill.sh <staging-base-url> <record-file>`)
writes `<record>.baseline.json`; the `--verify` phase appends the restored
database evidence and exits nonzero on drift.

- date_utc:
- operator (role, not personal data):
- staging_target:
- restored_target:
- image_digest (`k8s/.image-digest` first line):
- git_sha:
- flyway_version (`SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1`):
- backup_method + destination + backup_timestamp_utc:
- baseline_snapshot (`<record>.baseline.json`):
- baseline: users / accounts / transfers / audit_logs / ledger_entries counts + per-currency totals (paste `python3 ops/restore_verify.py --summary <record>.baseline.json`):
- restored_snapshot (`<record>.restored.json`):
- ledger reconcile: nonzero `transaction_ref` groups (must be none - paste `ops/restore-drill.sh --verify` output):
- totals reconcile: match / mismatch (details):
- smoke check: `ops/health_smoke.py --require-probes --json` output:
- automated_verification: PASS / FAIL (`ops/restore-drill.sh --verify` verdict):
- achieved data-loss interval vs RPO <= 15 min:
- achieved time-to-recovery vs RTO <= 1 h:
- gaps filed as issues (ids):
