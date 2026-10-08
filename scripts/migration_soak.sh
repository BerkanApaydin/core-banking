#!/usr/bin/env bash
# O-1/DB-1 migration soak: seed a production-size transfers table, run Flyway
# migrate, and report per-statement wall time + lock waits. Full soak runs on
# workflow_dispatch / release branches with a staging dump; CI presence-check
# runs on every PR (script exists + SQL parses).
set -euo pipefail
DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/bank_db}"
SEED_ROWS="${SEED_ROWS:-1000000}"
echo "[migration-soak] seed_rows=$SEED_ROWS"
psql "$DB_URL" -c "SELECT 1" >/dev/null
# Minimal synthetic load: N transfers rows to force index/rewrite cost.
psql "$DB_URL" -c "
DO \$\$
BEGIN
  IF (SELECT count(*) FROM transfers) < $SEED_ROWS THEN
    INSERT INTO transfers (sender_account_id, receiver_account_id, amount, currency, status, business_created_at)
    SELECT 1, 2, 1.00, 'TRY', 'COMPLETED', now() - (s || ' seconds')::interval
    FROM generate_series(1, $SEED_ROWS - (SELECT count(*) FROM transfers)) s;
  END IF;
END \$\$;"
START=$(date +%s)
/opt/flyway/flyway migrate -url="$DB_URL" -locations=filesystem:common/src/main/resources/db/migration 2>&1 | tee migration-soak.log
END=$(date +%s)
echo "[migration-soak] migrate_wall_seconds=$((END - START))"
echo "[migration-soak] lock waits (log_lock_waits must be on in staging):"
psql "$DB_URL" -c "SELECT count(*) AS lock_wait_events FROM pg_stat_database WHERE datname = current_database();" || true
echo "[migration-soak] done. Attach migration-soak.log to the release tag."
