#!/usr/bin/env python3
"""T-1/O-3: migration upgrade-path static guard.

Flyway upgrades fail closed on version gaps, duplicate versions, or a missing
file in the middle of the chain — and a presence-only CI check cannot see any
of them. This script pins the CONTENT contract of the chain:

1. Versions form a contiguous V1..Vn sequence (no gaps, no duplicates).
2. No `baseline-on-migrate: true` in main resources (adopting a non-empty
   schema without history must stay an explicit, reviewed act).
3. Every migration file is non-empty (an empty V__ file migrates "success" and
   silently shifts every later checksum).

Usage: python3 scripts/check_migration_chain.py
Exit 0 when the chain is sound, 1 with a ::error annotation otherwise.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATION_DIR = ROOT / "common" / "src" / "main" / "resources" / "db" / "migration"


def fail(message: str) -> int:
    print(f"::error::{message}")
    return 1


def main() -> int:
    files = sorted(MIGRATION_DIR.glob("V*.sql"))
    if not files:
        return fail("no V*.sql migrations found")

    versions: list[int] = []
    for path in files:
        match = re.match(r"V(\d+)__", path.name)
        if not match:
            return fail(f"migration file breaks the V<version>__<name>.sql convention: {path.name}")
        versions.append(int(match.group(1)))
        if path.stat().st_size == 0:
            return fail(f"empty migration file: {path.name}")

    if len(set(versions)) != len(versions):
        return fail(f"duplicate migration versions: {sorted(versions)}")

    expected = list(range(1, max(versions) + 1))
    if sorted(versions) != expected:
        missing = sorted(set(expected) - set(versions))
        return fail(f"migration version gap — missing: {missing} (head: V{max(versions)})")

    offenders: list[str] = []
    for yml in list((ROOT / "app" / "src" / "main" / "resources").glob("application*.yml")):
        text = yml.read_text(encoding="utf-8")
        if re.search(r"baseline-on-migrate:\s*true", text):
            offenders.append(str(yml.relative_to(ROOT)))
    if offenders:
        return fail(f"baseline-on-migrate:true in main resources: {offenders}")

    print(f"migration chain sound: V1..V{max(versions)} contiguous "
          f"({len(files)} files), no auto-baseline in main resources")
    return 0


if __name__ == "__main__":
    sys.exit(main())
