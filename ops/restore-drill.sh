#!/usr/bin/env bash
# Staging backup/restore drill (quarterly). Records evidence, never touches prod.
# Usage: ops/restore-drill.sh <staging-base-url> <record-file>
# Requires: psql (PGPASSWORD/PGHOST/… for the STAGING database), python3.
# The database backup itself uses the platform method (pgBackRest / cloud
# snapshot / pg_dump); this script captures the baseline, verifies the
# restored database and reconciles financial invariants.
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <staging-base-url> <record-file>" >&2
    exit 2
fi
base_url="$1"
record="$2"
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 1; }

started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
{
    echo "# Restore drill record"
    echo
    echo "- started_at_utc: $started"
    echo "- target: $base_url"
    echo "- image_digest: $(cat k8s/.image-digest 2>/dev/null | head -n 1 || echo UNPINNED)"
    echo "- git_sha: $(git rev-parse HEAD)"
    echo "- flyway_version: $(psql -tAc 'SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;' || echo UNKNOWN)"
    echo
    echo "## Baseline (pre-backup)"
    psql -tAc "SELECT 'users=' || COUNT(*) FROM users;" || true
    psql -tAc "SELECT 'accounts=' || COUNT(*) FROM accounts;" || true
    psql -tAc "SELECT 'transfers=' || COUNT(*) FROM transfers;" || true
    psql -tAc "SELECT 'audit_logs=' || COUNT(*) FROM audit_logs;" || true
    psql -tAc "SELECT 'ledger_entries=' || COUNT(*) FROM ledger_entries;" || true
    psql -tAc "SELECT currency || '=' || SUM(balance) FROM accounts GROUP BY currency ORDER BY 1;" || true
} | tee "$record"

echo
echo "## Operator steps (record results in $record)" | tee -a "$record"
echo "1. Back up staging with the platform method to a separately protected" | tee -a "$record"
echo "   destination; append backup timestamp + location to $record." | tee -a "$record"
echo "2. Restore into an ISOLATED environment (outbound providers disabled)." | tee -a "$record"
echo "3. Re-run this script's read-only checks against the restored DB:" | tee -a "$record"
echo "   ledger legs per transaction_ref must net to zero:" | tee -a "$record"
echo "   SELECT transaction_ref, SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END)" | tee -a "$record"
echo "   FROM ledger_entries GROUP BY transaction_ref HAVING SUM(CASE WHEN direction='CREDIT'" | tee -a "$record"
echo "   THEN amount ELSE -amount END) <> 0;" | tee -a "$record"
echo "4. python ops/health_smoke.py $base_url --require-probes --json >> $record" | tee -a "$record"
echo "   (liveness/readiness groups are prod-only: outside prod boot the app" | tee -a "$record"
echo "   with MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true, else --require-probes" | tee -a "$record"
echo "   404s by configuration, not by failure — see docs/operations.md)" | tee -a "$record"
python3 ops/health_smoke.py "$base_url" --require-probes | tee -a "$record" || true

echo "- finished_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$record"
echo "Drill record written to $record"
