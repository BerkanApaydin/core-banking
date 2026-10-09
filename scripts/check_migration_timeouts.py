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
            failures.append(sql.name)
    if failures:
        print("heavy migrations without SET LOCAL statement_timeout:", file=sys.stderr)
        for name in failures:
            print(f"  {name}", file=sys.stderr)
        print("Add `SET LOCAL statement_timeout = '10min';` (see V46).", file=sys.stderr)
        return 1
    print("migration timeouts ok.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
