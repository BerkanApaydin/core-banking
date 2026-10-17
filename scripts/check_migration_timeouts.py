"""Fail CI when a heavy migration lacks a statement_timeout lift.

HIGH-4: the per-role 30s statement_timeout (docker-init/init-db.sql) aborts
full-table UPDATE/VALIDATE/ALTER and large index builds on populated DBs.
Every migration containing a heavyweight statement must start with
`SET LOCAL statement_timeout = '10min';` (Flyway runs each migration in one
transaction, so SET LOCAL applies). CREATE INDEX CONCURRENTLY is illegal
inside Flyway's transaction — large indexes belong in
scripts/create_index_concurrently.sql instead.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS = ROOT / "common" / "src" / "main" / "resources" / "db" / "migration"

HEAVY = [
    re.compile(r"\bUPDATE\s+\w+\s+SET\b", re.IGNORECASE),
    re.compile(r"\bVALIDATE\s+CONSTRAINT\b", re.IGNORECASE),
    re.compile(r"\bALTER\s+TABLE\b[\s\S]*?\bTYPE\b", re.IGNORECASE),
]

# NOTE: plain CREATE INDEX is intentionally not gated here — small-table
# builds finish in ms, and large-table builds belong in
# scripts/create_index_concurrently.sql (CONCURRENTLY is illegal inside
# Flyway's transaction). Likewise ALTER TYPE ... ADD VALUE takes a type lock
# only (see V33/V50) and needs no lift.

# Audit follow-up: a lifted statement_timeout alone does not bound the deploy
# stall — without lock_timeout an ALTER waits indefinitely for ACCESS
# EXCLUSIVE behind a long transaction, wedging the rollout and piling the lock
# queue. Table-rewrite migrations must therefore ALSO fail fast on lock
# contention with `SET LOCAL lock_timeout = '30s';` (Flyway marks the migration
# failed; the operator retries in a maintenance window).
#
# GRANDFATHERED contains rewrites applied before this rule existed. Their
# checksums are frozen (validate-on-migrate is on) — editing them would break
# every existing database — so they are exempt; the rule guards new migrations.
GRANDFATHERED = {
    "V8__fix_column_types.sql",
    "V10__rename_active_to_status.sql",
    "V14__increase_outbox_payload_size.sql",
    "V30__tighten_enum_column_widths.sql",
    "V32__native_enum_types.sql",
    "V46__timestamptz.sql",
    "V53__widen_idempotency_response_body.sql",
}

REWRITE = [
    re.compile(r"\bALTER\s+TABLE\b[\s\S]*?\bTYPE\b", re.IGNORECASE),
]


def main() -> int:
    if not MIGRATIONS.is_dir():
        print(f"missing migrations dir: {MIGRATIONS}", file=sys.stderr)
        return 1
    failures = []
    for sql in sorted(MIGRATIONS.glob("V*.sql")):
        text = sql.read_text(encoding="utf-8")
        if not any(p.search(text) for p in HEAVY):
            continue
        if not re.search(r"SET\s+LOCAL\s+statement_timeout", text, re.IGNORECASE):
            failures.append(f"{sql.name} (missing SET LOCAL statement_timeout)")
        if (
            any(p.search(text) for p in REWRITE)
            and sql.name not in GRANDFATHERED
            and not re.search(r"SET\s+LOCAL\s+lock_timeout", text, re.IGNORECASE)
        ):
            failures.append(f"{sql.name} (table rewrite without SET LOCAL lock_timeout)")
    if failures:
        print("heavy migrations without timeout guards:", file=sys.stderr)
        for name in failures:
            print(f"  {name}", file=sys.stderr)
        print("Add `SET LOCAL statement_timeout = '10min';` (see V46), plus",
              "`SET LOCAL lock_timeout = '30s';` for table rewrites.", file=sys.stderr)
        return 1
    print("migration timeouts ok.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
