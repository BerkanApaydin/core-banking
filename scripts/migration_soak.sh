#!/usr/bin/env bash
# O-1/DB-1 migration soak: seed a production-size transfers table, run Flyway
# migrate, and report per-statement wall time + lock waits. Full soak runs on
# workflow_dispatch / release branches with a staging dump; the CI job runs the
# same script against a fresh database, migrating to the second-latest version,
# seeding, then applying the newest migration.
set -euo pipefail
PSQL_URL="${PSQL_URL:-postgresql://bank_user:bank_password@localhost:5432/bank_db}"
# Flyway talks JDBC, not libpq: keep its own URL and lift the role's 30s
# statement timeout for the session that runs one-off schema rewrites.
FLYWAY_URL="${FLYWAY_URL:-jdbc:postgresql://localhost:5432/bank_db?options=-c%20statement_timeout%3D10min%20-c%20idle_in_transaction_session_timeout%3D0}"
SEED_ROWS="${SEED_ROWS:-1000000}"
BASELINE_TARGET="${BASELINE_TARGET:-}"
# Lock waits are a release risk, not a metric: any ungranted/blocked lock or
# logged wait above this budget fails the soak. Staging dumps may raise it
# explicitly; CI keeps the fail-closed default.
MAX_LOCK_WAITS="${MAX_LOCK_WAITS:-0}"

if command -v flyway >/dev/null 2>&1; then
  FLYWAY_BIN="${FLYWAY_BIN:-flyway}"
else
  FLYWAY_BIN="${FLYWAY_BIN:-/opt/flyway/flyway}"
fi
if ! command -v "$FLYWAY_BIN" >/dev/null 2>&1 && [ ! -x "$FLYWAY_BIN" ]; then
  echo "[migration-soak] Flyway CLI not found (set FLYWAY_BIN)" >&2
  exit 1
fi

if [ -n "${DB_USERNAME:-}" ]; then
  export FLYWAY_USER="${FLYWAY_USER:-$DB_USERNAME}"
fi
if [ -n "${DB_PASSWORD:-}" ]; then
  export FLYWAY_PASSWORD="${FLYWAY_PASSWORD:-$DB_PASSWORD}"
fi

run_psql() {
  psql "$PSQL_URL" -v ON_ERROR_STOP=1 "$@"
}

run_flyway() {
  "$FLYWAY_BIN" "$@" \
    -url="$FLYWAY_URL" \
    -locations=filesystem:migrations/src/main/resources/db/migration
}

echo "[migration-soak] seed_rows=$SEED_ROWS baseline_target=${BASELINE_TARGET:-<current>}"
run_psql -c "SELECT 1" >/dev/null

if [ -n "$BASELINE_TARGET" ]; then
  echo "[migration-soak] migrating to baseline V$BASELINE_TARGET"
  run_flyway migrate -target="$BASELINE_TARGET"
fi

# Synthetic load: N transfers rows against real accounts (V24 restores the
# sender/receiver FKs). The seed user/accounts only exist when the database has
# no usable accounts — a staging dump keeps its own rows.
run_psql <<SQL
SET statement_timeout = '10min';
SET idle_in_transaction_session_timeout = 0;
DO \$\$
DECLARE
  sender_id BIGINT;
  receiver_id BIGINT;
  existing BIGINT;
BEGIN
  SELECT count(*) INTO existing FROM transfers;
  IF existing >= $SEED_ROWS THEN
    RAISE NOTICE 'transfers already has % rows; seed skipped', existing;
    RETURN;
  END IF;

  INSERT INTO users (username, password, role, created_at, version)
  VALUES ('migration_soak_user', 'soak-not-a-login-account', 'ROLE_USER', now(), 0)
  ON CONFLICT (username) DO NOTHING;

  INSERT INTO accounts (user_id, iban, owner_name, balance, currency, status, version, created_at)
  SELECT u.id, 'TR000000000000000000000001', 'Migration Soak', 1000000000.00, 'TRY', 'ACTIVE', 0, now()
  FROM users u WHERE u.username = 'migration_soak_user'
  ON CONFLICT (iban) DO NOTHING;

  INSERT INTO accounts (user_id, iban, owner_name, balance, currency, status, version, created_at)
  SELECT u.id, 'TR000000000000000000000002', 'Migration Soak', 1000000000.00, 'TRY', 'ACTIVE', 0, now()
  FROM users u WHERE u.username = 'migration_soak_user'
  ON CONFLICT (iban) DO NOTHING;

  SELECT id INTO sender_id FROM accounts ORDER BY id LIMIT 1;
  SELECT id INTO receiver_id FROM accounts WHERE id <> sender_id ORDER BY id LIMIT 1;
  IF sender_id IS NULL OR receiver_id IS NULL THEN
    RAISE EXCEPTION 'migration soak requires at least two accounts';
  END IF;

  INSERT INTO transfers (sender_account_id, receiver_account_id, amount, currency, status,
                         created_at, business_created_at)
  SELECT sender_id, receiver_id, 1.00, 'TRY', 'COMPLETED',
         now() - (s || ' seconds')::interval,
         now() - (s || ' seconds')::interval
  FROM generate_series(1, $SEED_ROWS - existing) s;
END \$\$;
SQL

START=$(date +%s)
run_flyway migrate 2>&1 | tee migration-soak.log
END=$(date +%s)
echo "[migration-soak] migrate_wall_seconds=$((END - START))"

echo "[migration-soak] current lock waits (point-in-time; log_lock_waits also records events):"
UNGRANTED_LOCKS=$(run_psql -Atc "SELECT count(*) FROM pg_locks WHERE NOT granted;")
BLOCKED_SESSIONS=$(run_psql -Atc "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock';")
LOGGED_LOCK_WAITS=$(grep -c 'still waiting for' migration-soak.log || true)
echo "[migration-soak] ungranted_locks=$UNGRANTED_LOCKS"
echo "[migration-soak] blocked_sessions=$BLOCKED_SESSIONS"
echo "[migration-soak] logged lock waits: $LOGGED_LOCK_WAITS (budget $MAX_LOCK_WAITS)"

if [ "$UNGRANTED_LOCKS" -ne 0 ] || [ "$BLOCKED_SESSIONS" -ne 0 ] \
    || [ "$LOGGED_LOCK_WAITS" -gt "$MAX_LOCK_WAITS" ]; then
  echo "::error::migration soak exceeded the lock-wait budget" >&2
  exit 1
fi
echo "[migration-soak] done. Attach migration-soak.log to the release tag."
