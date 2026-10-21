#!/usr/bin/env bash
# Staging backup/restore drill (quarterly). Records evidence, never touches prod.
#
# Two phases, because the backup/restore itself is a platform operation:
#
#   ops/restore-drill.sh <staging-base-url> <record-file>
#       Capture the pre-backup baseline and write <record>.baseline.json.
#       Run immediately before the backup with staging writes quiesced.
#
#   ops/restore-drill.sh --verify <restored-base-url> <record-file> [restored-database-url]
#       Capture the restored database, compare it with the baseline, run the
#       restored app smoke check, append the evidence to the record, and fail
#       closed on any drift. The URL may be omitted when PG* env vars point at
#       the restored database.
#
# Requires: psql, python3. Baseline credentials come from PGHOST/PGUSER/
# PGPASSWORD/PGDATABASE; verify credentials come from the optional URL or the
# same PG* variables pointed at the restored instance.
set -euo pipefail

usage() {
    cat >&2 <<'EOF'
Usage:
  ops/restore-drill.sh <staging-base-url> <record-file>
  ops/restore-drill.sh --verify <restored-base-url> <record-file> [restored-database-url]

Baseline phase: capture the pre-backup database snapshot (writes
<record>.baseline.json) and the staging smoke check.

Verify phase: compare the restored database with the baseline, run the
restored app smoke check, append the evidence to <record>, and exit nonzero
on any mismatch. The restored database URL can also be supplied through
RESTORED_DATABASE_URL.
EOF
    exit 2
}

mode="baseline"
if [ "${1:-}" = "--verify" ]; then
    mode="verify"
    shift
fi
if [ "$mode" = "baseline" ]; then
    [ "$#" -eq 2 ] || usage
else
    [ "$#" -ge 2 ] && [ "$#" -le 3 ] || usage
fi

base_url="$1"
record="$2"
restored_url="${3:-${RESTORED_DATABASE_URL:-}}"

command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }
[ -d "$(dirname "$record")" ] || {
    echo "record directory does not exist: $(dirname "$record")" >&2
    exit 2
}

case "$record" in
    *.md) stem="${record%.md}" ;;
    *) stem="$record" ;;
esac
baseline_snapshot="${stem}.baseline.json"
restored_snapshot="${stem}.restored.json"

record_tmp=""
snapshot_tmp=""
section_tmp=""
err_file="$(mktemp)"
trap 'rm -f "${record_tmp:-}" "${snapshot_tmp:-}" "${section_tmp:-}" "$err_file"' EXIT

capture_snapshot() {
    local target="${1:-}"
    local -a args=(-v ON_ERROR_STOP=1 -X -q -t -A)
    if [ -n "$target" ]; then
        args+=("$target")
    fi
    psql "${args[@]}" <<'SQL'
SELECT json_build_object(
    'schema_version', 1,
    'captured_at_utc', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'flyway_version', (
        SELECT version FROM flyway_schema_history
        ORDER BY installed_rank DESC LIMIT 1
    ),
    'counts', json_build_object(
        'users', (SELECT count(*) FROM users),
        'accounts', (SELECT count(*) FROM accounts),
        'transfers', (SELECT count(*) FROM transfers),
        'audit_logs', (SELECT count(*) FROM audit_logs),
        'ledger_entries', (SELECT count(*) FROM ledger_entries),
        'idempotency_keys', (SELECT count(*) FROM idempotency_keys),
        'outbox_events', (SELECT count(*) FROM outbox_events),
        'outbox_pending', (SELECT count(*) FROM outbox_events WHERE NOT processed),
        'outbox_processed', (SELECT count(*) FROM outbox_events WHERE processed),
        'token_revocations', (SELECT count(*) FROM token_revocations),
        'refresh_tokens', (SELECT count(*) FROM refresh_tokens)
    ),
    'totals', (
        SELECT COALESCE(json_object_agg(currency, total), '{}'::json)
        FROM (
            SELECT currency::text AS currency, SUM(balance)::text AS total
            FROM accounts
            GROUP BY currency::text
        ) totals_by_currency
    ),
    'ledger_nonzero_refs', (
        SELECT count(*)
        FROM (
            SELECT transaction_ref
            FROM ledger_entries
            GROUP BY transaction_ref
            HAVING SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) <> 0
        ) unbalanced
    )
);
SQL
}

snapshot_field() {
    local snapshot="$1" field="$2"
    python3 -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    snapshot = json.load(handle)
print(snapshot[sys.argv[2]])
' "$snapshot" "$field"
}

if [ "$mode" = "baseline" ]; then
    if [ -e "$record" ] || [ -e "$baseline_snapshot" ]; then
        echo "Refusing to overwrite drill evidence: $record or $baseline_snapshot exists." >&2
        echo "Use a new record file for a new drill." >&2
        exit 1
    fi

    started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "Capturing pre-backup baseline from the staging database..." >&2
    snapshot_json="$(capture_snapshot)"

    snapshot_tmp="${baseline_snapshot}.tmp.$$"
    printf '%s\n' "$snapshot_json" > "$snapshot_tmp"
    if ! baseline_summary="$(python3 ops/restore_verify.py --summary "$snapshot_tmp" 2>"$err_file")"; then
        echo "Baseline snapshot is invalid: $(cat "$err_file")" >&2
        exit 1
    fi
    flyway_version="$(snapshot_field "$snapshot_tmp" flyway_version)"

    smoke_ok=1
    smoke_output="$(python3 ops/health_smoke.py "$base_url" --require-probes --json 2>&1)" || smoke_ok=0

    image_digest="UNPINNED"
    if [ -f k8s/.image-digest ]; then
        image_digest="$(head -n 1 k8s/.image-digest)"
    fi
    git_sha="$(git rev-parse HEAD)" || {
        echo "Could not resolve the current git commit." >&2
        exit 1
    }

    record_tmp="${record}.tmp.$$"
    {
        echo "# Restore drill record"
        echo
        echo "- started_at_utc: $started"
        echo "- staging_target: $base_url"
        echo "- image_digest: $image_digest"
        echo "- git_sha: $git_sha"
        echo "- flyway_version: $flyway_version"
        echo "- baseline_snapshot: $baseline_snapshot"
        echo "- baseline_health: $([ "$smoke_ok" -eq 1 ] && echo PASS || echo FAIL)"
        echo
        echo "## Baseline (pre-backup)"
        printf '%s\n' "$baseline_summary"
        echo
        echo "## Staging health smoke"
        printf '%s\n' "$smoke_output"
        echo
        echo "## Operator steps"
        echo "1. Back up staging with the platform method to a separately protected"
        echo "   destination; append backup_method, destination and"
        echo "   backup_timestamp_utc to $record."
        echo "2. Restore into an ISOLATED environment (outbound providers disabled)."
        echo "3. Point a compatible application version at the restored database."
        echo "4. Verify the restore and the restored app:"
        echo "   RESTORED_DATABASE_URL=postgresql://... \\"
        echo "     ops/restore-drill.sh --verify <restored-base-url> $record"
        echo "   The verifier compares Flyway, every table count, per-currency"
        echo "   totals and the ledger net-zero invariant, then runs the smoke"
        echo "   check against <restored-base-url>. Any drift exits nonzero."
        echo "5. Record achieved RPO/RTO and file gaps as issues."
        echo
        echo "- baseline_finished_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$record_tmp"

    mv "$snapshot_tmp" "$baseline_snapshot"
    mv "$record_tmp" "$record"
    cat "$record"

    if [ "$smoke_ok" -ne 1 ]; then
        echo "Staging smoke check FAILED; the baseline record is written but the drill is not valid." >&2
        exit 1
    fi
    echo "Baseline captured. Back up, restore, then run --verify." >&2
    exit 0
fi

# Verify phase.
if [ ! -f "$record" ]; then
    echo "Drill record not found: $record (run the baseline phase first)." >&2
    exit 1
fi
if [ ! -f "$baseline_snapshot" ]; then
    echo "Baseline snapshot not found: $baseline_snapshot" >&2
    echo "Run the baseline phase first; legacy records without a snapshot cannot be verified." >&2
    exit 1
fi

# A failed capture must not be compared with a previous run's snapshot.
rm -f "$restored_snapshot"

started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
verify_ok=1
snapshot_error=""
comparison=""
comparison_ok=1
smoke_ok=1
smoke_output=""

echo "Capturing restored database snapshot..." >&2
if snapshot_json="$(capture_snapshot "$restored_url" 2>"$err_file")"; then
    snapshot_tmp="${restored_snapshot}.tmp.$$"
    printf '%s\n' "$snapshot_json" > "$snapshot_tmp"
    if python3 ops/restore_verify.py --summary "$snapshot_tmp" >/dev/null 2>"$err_file"; then
        mv "$snapshot_tmp" "$restored_snapshot"
    else
        verify_ok=0
        snapshot_error="$(cat "$err_file")"
    fi
else
    verify_ok=0
    snapshot_error="$(cat "$err_file")"
fi

if [ -f "$restored_snapshot" ]; then
    if ! comparison="$(python3 ops/restore_verify.py "$baseline_snapshot" "$restored_snapshot" 2>&1)"; then
        verify_ok=0
        comparison_ok=0
    fi
else
    comparison="restored snapshot was not captured"
    comparison_ok=0
fi

smoke_output="$(python3 ops/health_smoke.py "$base_url" --require-probes --json 2>&1)" || {
    smoke_ok=0
    verify_ok=0
}

restored_flyway="UNKNOWN"
ledger_state="FAIL (no restored snapshot)"
totals_state="FAIL (no restored snapshot)"
if [ -f "$restored_snapshot" ]; then
    restored_flyway="$(snapshot_field "$restored_snapshot" flyway_version)"
    ledger_nonzero="$(snapshot_field "$restored_snapshot" ledger_nonzero_refs)"
    baseline_ledger="$(snapshot_field "$baseline_snapshot" ledger_nonzero_refs)"
    if [ "$baseline_ledger" = "0" ] && [ "$ledger_nonzero" = "0" ]; then
        ledger_state="PASS (baseline and restored: 0 nonzero transaction_ref groups)"
    else
        ledger_state="FAIL (baseline=$baseline_ledger, restored=$ledger_nonzero nonzero transaction_ref groups)"
        verify_ok=0
    fi
    if [ "$comparison_ok" -eq 1 ]; then
        totals_state="PASS (all table counts and per-currency totals match)"
    else
        totals_state="FAIL (see snapshot comparison)"
    fi
fi

section_tmp="$(mktemp)"
{
    echo
    echo "## Verification (restored database)"
    echo "- verify_started_at_utc: $started"
    echo "- restored_target: $base_url"
    echo "- restored_snapshot: $restored_snapshot"
    echo "- flyway_version: $restored_flyway"
    echo "- ledger reconcile: $ledger_state"
    echo "- totals reconcile: $totals_state"
    echo "- smoke check: $([ "$smoke_ok" -eq 1 ] && echo PASS || echo FAIL)"
    echo "- automated_verification: $([ "$verify_ok" -eq 1 ] && echo PASS || echo FAIL)"
    echo
    echo "### Snapshot comparison"
    if [ -n "$snapshot_error" ]; then
        echo "restored snapshot error: $snapshot_error"
    fi
    printf '%s\n' "$comparison"
    echo
    echo "### Restored health smoke"
    printf '%s\n' "$smoke_output"
    echo
    echo "- verify_finished_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$section_tmp"

cat "$section_tmp" >> "$record"
cat "$section_tmp"

if [ "$verify_ok" -ne 1 ]; then
    echo "Restore verification FAILED; see $record." >&2
    exit 1
fi
echo "Restore verification passed; evidence appended to $record." >&2
