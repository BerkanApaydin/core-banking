#!/usr/bin/env python3
"""Compare pre-backup and post-restore database snapshots for the drill.

The restore drill used to capture only a baseline and leave the restored
database unchecked. This helper makes the verification machine-checkable:

* the Flyway version must match;
* every tracked table count must match;
* every per-currency account total must match exactly;
* the ledger net-zero invariant must hold in both snapshots;
* the restored snapshot must contain the same snapshot schema.

Usage:
  python3 ops/restore_verify.py --summary snapshot.json
  python3 ops/restore_verify.py baseline.json restored.json
  python3 ops/restore_verify.py --self-test

Exit 0 on a match, 1 on any drift or malformed snapshot.
"""

import argparse
from decimal import Decimal, InvalidOperation
import json
import sys

SCHEMA_VERSION = 1
REQUIRED_COUNTS = (
    "users",
    "accounts",
    "transfers",
    "audit_logs",
    "ledger_entries",
    "idempotency_keys",
    "outbox_events",
    "outbox_pending",
    "outbox_processed",
    "token_revocations",
    "refresh_tokens",
)
REQUIRED_TOP = ("schema_version", "flyway_version", "counts", "totals",
                "ledger_nonzero_refs")


def load(path: str) -> dict:
    with open(path, encoding="utf-8") as handle:
        snapshot = json.load(handle)
    validate(snapshot, path)
    return snapshot


def validate(snapshot: dict, source: str = "snapshot") -> None:
    if not isinstance(snapshot, dict):
        raise ValueError(f"{source}: snapshot must be a JSON object")
    for key in REQUIRED_TOP:
        if key not in snapshot:
            raise ValueError(f"{source}: missing required field {key!r}")
    if snapshot["schema_version"] != SCHEMA_VERSION:
        raise ValueError(
            f"{source}: unsupported snapshot schema_version "
            f"{snapshot['schema_version']!r} (expected {SCHEMA_VERSION})"
        )
    version = snapshot["flyway_version"]
    if not isinstance(version, str) or not version.strip():
        raise ValueError(f"{source}: flyway_version must be a non-empty string")
    counts = snapshot["counts"]
    if not isinstance(counts, dict):
        raise ValueError(f"{source}: counts must be an object")
    for key in REQUIRED_COUNTS:
        if key not in counts:
            raise ValueError(f"{source}: counts is missing {key!r}")
        value = counts[key]
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ValueError(f"{source}: counts.{key} must be a non-negative integer")
    totals = snapshot["totals"]
    if not isinstance(totals, dict):
        raise ValueError(f"{source}: totals must be an object")
    for currency, total in totals.items():
        decimal(total, f"{source}: totals.{currency}")
    ledger = snapshot["ledger_nonzero_refs"]
    if not isinstance(ledger, int) or isinstance(ledger, bool) or ledger < 0:
        raise ValueError(
            f"{source}: ledger_nonzero_refs must be a non-negative integer"
        )


def decimal(value, label: str) -> Decimal:
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError) as exc:
        raise ValueError(f"{label} must be a decimal number, got {value!r}") from exc


def compare(baseline: dict, restored: dict) -> list[str]:
    problems: list[str] = []
    if baseline["schema_version"] != restored["schema_version"]:
        problems.append(
            "snapshot schema_version differs: "
            f"{baseline['schema_version']} != {restored['schema_version']}"
        )
    if baseline["flyway_version"] != restored["flyway_version"]:
        problems.append(
            "flyway_version differs: "
            f"{baseline['flyway_version']} != {restored['flyway_version']}"
        )

    baseline_counts = baseline["counts"]
    restored_counts = restored["counts"]
    for key in sorted(set(baseline_counts) | set(restored_counts)):
        if key not in restored_counts:
            problems.append(f"restored snapshot is missing count {key!r}")
        elif key not in baseline_counts:
            problems.append(f"restored snapshot has an unexpected count {key!r}")
        elif baseline_counts[key] != restored_counts[key]:
            problems.append(
                f"count {key} differs: baseline={baseline_counts[key]} "
                f"restored={restored_counts[key]}"
            )

    baseline_totals = baseline["totals"]
    restored_totals = restored["totals"]
    for currency in sorted(set(baseline_totals) | set(restored_totals)):
        if currency not in restored_totals:
            problems.append(f"restored snapshot is missing currency {currency!r}")
        elif currency not in baseline_totals:
            problems.append(f"restored snapshot has an unexpected currency {currency!r}")
        else:
            baseline_total = decimal(baseline_totals[currency], "baseline total")
            restored_total = decimal(restored_totals[currency], "restored total")
            if baseline_total != restored_total:
                problems.append(
                    f"total {currency} differs: baseline={baseline_total} "
                    f"restored={restored_total}"
                )

    if baseline["ledger_nonzero_refs"] != 0:
        problems.append(
            "baseline already violates the ledger net-zero invariant: "
            f"{baseline['ledger_nonzero_refs']} transaction_ref group(s)"
        )
    if restored["ledger_nonzero_refs"] != 0:
        problems.append(
            "restored database violates the ledger net-zero invariant: "
            f"{restored['ledger_nonzero_refs']} transaction_ref group(s)"
        )
    return problems


def print_summary(snapshot: dict) -> None:
    print(f"- flyway_version: {snapshot['flyway_version']}")
    for key in REQUIRED_COUNTS:
        print(f"- {key}: {snapshot['counts'][key]}")
    for currency in sorted(snapshot["totals"]):
        print(f"- total {currency}: {snapshot['totals'][currency]}")
    print(f"- ledger_nonzero_refs: {snapshot['ledger_nonzero_refs']}")
    if snapshot.get("captured_at_utc"):
        print(f"- captured_at_utc: {snapshot['captured_at_utc']}")


def sample_snapshot() -> dict:
    return {
        "schema_version": 1,
        "captured_at_utc": "2026-10-21T00:00:00Z",
        "flyway_version": "54",
        "counts": {
            "users": 12,
            "accounts": 8,
            "transfers": 4,
            "audit_logs": 20,
            "ledger_entries": 8,
            "idempotency_keys": 3,
            "outbox_events": 2,
            "outbox_pending": 1,
            "outbox_processed": 1,
            "token_revocations": 1,
            "refresh_tokens": 5,
        },
        "totals": {"TRY": "1234.50", "USD": "99.00"},
        "ledger_nonzero_refs": 0,
    }


def self_test() -> int:
    failures: list[str] = []

    def expect(name: str, baseline: dict, restored: dict, should_fail: bool) -> None:
        problems = compare(baseline, restored)
        if bool(problems) is not should_fail:
            failures.append(f"{name}: expected {'failure' if should_fail else 'match'}, got {problems}")

    base = sample_snapshot()
    expect("identical", base, sample_snapshot(), False)

    changed = sample_snapshot()
    changed["counts"]["users"] += 1
    expect("count drift", base, changed, True)

    changed = sample_snapshot()
    changed["totals"]["TRY"] = "1234.51"
    expect("total drift", base, changed, True)

    changed = sample_snapshot()
    changed["flyway_version"] = "55"
    expect("flyway drift", base, changed, True)

    changed = sample_snapshot()
    changed["ledger_nonzero_refs"] = 1
    expect("restored ledger imbalance", base, changed, True)

    changed = sample_snapshot()
    changed["ledger_nonzero_refs"] = 2
    expect("baseline ledger imbalance", changed, sample_snapshot(), True)

    changed = sample_snapshot()
    del changed["counts"]["refresh_tokens"]
    expect("missing required count", base, changed, True)

    changed = sample_snapshot()
    changed["totals"]["EUR"] = "1.00"
    expect("unexpected currency", base, changed, True)

    changed = sample_snapshot()
    changed["schema_version"] = 2
    expect("schema drift", base, changed, True)

    numeric = sample_snapshot()
    numeric["totals"]["TRY"] = 1234.5
    expect("numeric totals are equivalent", base, numeric, False)

    invalid = sample_snapshot()
    invalid["counts"]["users"] = -1
    try:
        validate(invalid, "self-test")
    except ValueError:
        pass
    else:
        failures.append("negative count was accepted")

    if failures:
        print("restore verification self-test FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("restore verification self-test OK - 11 vectors.")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", nargs="?", help="pre-backup snapshot JSON")
    parser.add_argument("restored", nargs="?", help="post-restore snapshot JSON")
    parser.add_argument("--summary", metavar="SNAPSHOT",
                        help="print a human-readable snapshot summary")
    parser.add_argument("--self-test", action="store_true",
                        help="run the comparison regression vectors")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if args.summary:
        try:
            print_summary(load(args.summary))
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            print(f"restore verification error: {exc}", file=sys.stderr)
            return 1
        return 0
    if not args.baseline or not args.restored:
        parser.error("provide baseline.json and restored.json, or --summary/--self-test")

    try:
        baseline = load(args.baseline)
        restored = load(args.restored)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"restore verification error: {exc}", file=sys.stderr)
        return 1

    problems = compare(baseline, restored)
    if problems:
        print("restore verification FAILED:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    counts = len(restored["counts"])
    currencies = ", ".join(sorted(restored["totals"])) or "none"
    print(
        "restore verification PASSED: "
        f"flyway {restored['flyway_version']}, {counts} table counts, "
        f"currencies {currencies}, ledger net-zero"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
