#!/usr/bin/env python3
"""Fail CI when a heavy migration lacks its timeout guards.

HIGH-4: the per-role 30s statement_timeout (docker-init/init-db.sql) aborts
full-table UPDATE/VALIDATE/ALTER and large index builds on populated DBs.
Every migration containing a heavyweight statement must run
``SET LOCAL statement_timeout = '10min';`` BEFORE that statement (Flyway runs
each migration in one transaction, so SET LOCAL applies). CREATE INDEX
CONCURRENTLY is illegal inside Flyway's transaction - large indexes belong in
scripts/create_index_concurrently.sql instead.

Audit follow-up (lock_timeout): a lifted statement_timeout alone does not bound
the deploy stall - without lock_timeout an ALTER waits indefinitely for ACCESS
EXCLUSIVE behind a long transaction, wedging the rollout and piling the lock
queue. Table-rewrite / table-scan migrations must therefore ALSO fail fast with
``SET LOCAL lock_timeout = '30s';`` before the statement.

Statements are classified after stripping comments and string/dollar-quoted
literals, so commented-out SQL and semicolons inside literals cannot hide or
fake a heavy statement.

Run: ``python scripts/check_migration_timeouts.py``
Self-test: ``python scripts/check_migration_timeouts.py --self-test``
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS = ROOT / "migrations" / "src" / "main" / "resources" / "db" / "migration"

# GRANDFATHERED contains rewrites applied before this rule existed. Their
# checksums are frozen (validate-on-migrate is on) - editing them would break
# every existing database - so they are exempt; the rule guards new migrations.
GRANDFATHERED = {
    "V3__integrity_and_improvements.sql",
    "V6__update_transfers_status_check.sql",
    "V7__outbox_retry_and_idempotency_status.sql",
    "V8__fix_column_types.sql",
    "V9__add_outbox_partition.sql",
    "V10__rename_active_to_status.sql",
    "V12__add_version_to_users.sql",
    "V14__increase_outbox_payload_size.sql",
    "V24__restore_financial_references.sql",
    "V28__harden_integrity_and_report_performance.sql",
    "V30__tighten_enum_column_widths.sql",
    "V32__native_enum_types.sql",
    "V36__backfill_and_lock_optimistic_versions.sql",
    "V38__idempotency_key_kind.sql",
    "V39__user_token_version.sql",
    "V46__timestamptz.sql",
    "V53__widen_idempotency_response_body.sql",
    "V54__mfa.sql",
    "V56__mfa_totp_replay_and_index_cleanup.sql",
    "V60__idempotency_replayable_flag.sql",
}


def strip_sql_comments(text):
    """Blank comments and string/dollar-quoted literals, keeping newlines."""
    out = list(text)
    i, n = 0, len(text)

    def blank(start, end):
        for j in range(start, end):
            if out[j] != "\n":
                out[j] = " "

    while i < n:
        if text.startswith("--", i):
            end = text.find("\n", i)
            end = n if end < 0 else end
            blank(i, end)
            i = end
        elif text.startswith("/*", i):
            depth = 1
            j = i + 2
            while j < n and depth:
                if text.startswith("/*", j):
                    depth += 1
                    j += 2
                elif text.startswith("*/", j):
                    depth -= 1
                    j += 2
                else:
                    j += 1
            blank(i, j)
            i = j
        elif text[i] == "'":
            j = i + 1
            while j < n:
                if text[j] == "'":
                    if j + 1 < n and text[j + 1] == "'":
                        j += 2
                        continue
                    j += 1
                    break
                j += 1
            blank(i, j)
            i = j
        elif text[i] == '"':
            j = i + 1
            while j < n:
                if text[j] == '"':
                    if j + 1 < n and text[j + 1] == '"':
                        j += 2
                        continue
                    j += 1
                    break
                j += 1
            blank(i, j)
            i = j
        elif text[i] == "$":
            match = re.match(r"\$(?:[A-Za-z_][A-Za-z0-9_]*)?\$", text[i:])
            if not match:
                i += 1
                continue
            tag = match.group(0)
            end = text.find(tag, i + len(tag))
            if end < 0:
                blank(i, n)
                i = n
            else:
                blank(i, end + len(tag))
                i = end + len(tag)
        else:
            i += 1
    return "".join(out)


def split_statements(cleaned):
    return [part.strip() for part in cleaned.split(";") if part.strip()]


def normalize(statement):
    return " ".join(statement.split())


def _adds_non_null_default(statement):
    if not re.search(r"\bADD\s+(?:COLUMN\s+)?", statement, re.IGNORECASE):
        return False
    match = re.search(r"\bDEFAULT\b\s*([^\s,)]*)", statement, re.IGNORECASE)
    if not match:
        return False
    value = match.group(1).strip().upper()
    return not value.startswith("NULL")


def _adds_generated_stored(statement):
    return bool(re.search(
        r"^ALTER\s+TABLE\b.*\bADD\s+(?:COLUMN\s+)?.*\bGENERATED\b.*\bSTORED\b",
        statement, re.IGNORECASE))


def is_rewrite(statement):
    """Statements that rewrite or scan a table (need lock_timeout)."""
    if _adds_non_null_default(statement) or _adds_generated_stored(statement):
        return True
    return bool(
        re.search(
            r"^ALTER\s+TABLE\b.*\bALTER\s+(?:COLUMN\s+)?\S+\s+"
            r"(?:SET\s+DATA\s+)?TYPE\b", statement, re.IGNORECASE)
        or re.search(r"^ALTER\s+TABLE\b.*\bSET\s+NOT\s+NULL\b",
                     statement, re.IGNORECASE)
        or re.search(r"^ALTER\s+TABLE\b.*\bADD\s+CONSTRAINT\b",
                     statement, re.IGNORECASE)
        or re.search(r"^ALTER\s+TABLE\b.*\bSET\s+TABLESPACE\b",
                     statement, re.IGNORECASE)
        or re.match(r"^VACUUM\s+FULL\b", statement, re.IGNORECASE)
        or re.match(r"^CLUSTER\b", statement, re.IGNORECASE)
        or re.match(r"^REINDEX\b", statement, re.IGNORECASE)
    )


def is_heavy(statement):
    """Statements that can run long (need statement_timeout)."""
    if re.search(r"\bUPDATE\b.*\bSET\b", statement, re.IGNORECASE):
        return True
    if re.search(r"\bVALIDATE\s+CONSTRAINT\b", statement, re.IGNORECASE):
        return True
    if _adds_non_null_default(statement) or _adds_generated_stored(statement):
        return True
    if re.search(
            r"^ALTER\s+TABLE\b.*\bALTER\s+(?:COLUMN\s+)?\S+\s+"
            r"(?:SET\s+DATA\s+)?TYPE\b", statement, re.IGNORECASE):
        return True
    if re.search(r"^ALTER\s+TABLE\b.*\bSET\s+NOT\s+NULL\b",
                 statement, re.IGNORECASE):
        return True
    if re.search(r"^ALTER\s+TABLE\b.*\bSET\s+TABLESPACE\b",
                 statement, re.IGNORECASE):
        return True
    if re.search(r"^ALTER\s+TABLE\b.*\bADD\s+CONSTRAINT\b",
                 statement, re.IGNORECASE) and not re.search(
                     r"\bNOT\s+VALID\b", statement, re.IGNORECASE):
        return True
    if re.match(r"^VACUUM\s+FULL\b", statement, re.IGNORECASE):
        return True
    if re.match(r"^CLUSTER\b", statement, re.IGNORECASE):
        return True
    if re.match(r"^REINDEX\b", statement, re.IGNORECASE):
        return True
    return False


def check_migration(name, text, grandfathered=None):
    """Return a list of guard violations for one migration's SQL text."""
    if grandfathered is None:
        grandfathered = GRANDFATHERED
    if name in grandfathered:
        return []
    statements = [normalize(s) for s in split_statements(strip_sql_comments(text))]
    first_heavy = first_rewrite = None
    statement_timeout = lock_timeout = None
    for index, statement in enumerate(statements):
        if re.match(r"^SET\s+LOCAL\s+statement_timeout\b", statement, re.IGNORECASE):
            if statement_timeout is None:
                statement_timeout = index
        elif re.match(r"^SET\s+LOCAL\s+lock_timeout\b", statement, re.IGNORECASE):
            if lock_timeout is None:
                lock_timeout = index
        if is_heavy(statement) and first_heavy is None:
            first_heavy = index
        if is_rewrite(statement) and first_rewrite is None:
            first_rewrite = index

    issues = []
    if first_heavy is not None and (
            statement_timeout is None or statement_timeout > first_heavy):
        issues.append("missing SET LOCAL statement_timeout before heavy statement")
    if first_rewrite is not None and (
            lock_timeout is None or lock_timeout > first_rewrite):
        issues.append("missing SET LOCAL lock_timeout before table rewrite")
    return issues


def self_test():
    """Regression vectors for the classifier and ordering checks."""
    failures = []

    def expect(name, sql, want):
        got = check_migration(name, sql, grandfathered=set())
        if bool(got) is not want:
            failures.append(f"{name} expected {'fail' if want else 'pass'}: {got}")

    expect("update", "UPDATE accounts SET balance = 0;", True)
    expect("update_only", "UPDATE ONLY accounts SET balance = 0;", True)
    expect("update_guarded", "SET LOCAL statement_timeout = '10min';\n"
                             "UPDATE accounts SET balance = 0;", False)
    expect("update_late_timeout", "UPDATE accounts SET balance = 0;\n"
                                  "SET LOCAL statement_timeout = '10min';", True)
    expect("commented_update", "-- UPDATE accounts SET balance = 0;\nSELECT 1;", False)
    expect("type_guarded", "SET LOCAL statement_timeout = '10min';\n"
                           "SET LOCAL lock_timeout = '30s';\n"
                           "ALTER TABLE accounts ALTER COLUMN balance TYPE numeric(19,2);", False)
    expect("type_no_lock", "SET LOCAL statement_timeout = '10min';\n"
                           "ALTER TABLE accounts ALTER COLUMN balance TYPE numeric(19,2);", True)
    expect("default_null", "ALTER TABLE accounts ADD COLUMN note text DEFAULT NULL;", False)
    expect("default_value", "ALTER TABLE accounts ADD COLUMN score int DEFAULT 0;", True)
    expect("constraint_not_valid", "SET LOCAL lock_timeout = '30s';\n"
                                   "ALTER TABLE accounts ADD CONSTRAINT c CHECK (score > 0) NOT VALID;", False)
    expect("constraint_valid", "SET LOCAL statement_timeout = '10min';\n"
                               "ALTER TABLE accounts ADD CONSTRAINT c CHECK (score > 0);", True)
    expect("alter_type_add_value", "ALTER TYPE status ADD VALUE 'NEW';", False)
    expect("create_index", "CREATE INDEX idx ON accounts (balance);", False)
    expect("string_semicolon", "SET LOCAL statement_timeout = '10min';\n"
                               "INSERT INTO notes VALUES ('a; UPDATE x SET y = 1;');", False)
    expect("dollar_body", "CREATE FUNCTION f() RETURNS void AS $$ "
                          "UPDATE x SET y = 1; $$ LANGUAGE plpgsql;", False)
    expect("vacuum_full", "VACUUM FULL accounts;", True)
    if check_migration("V8__fix_column_types.sql",
                       "ALTER TABLE accounts ALTER COLUMN balance TYPE numeric(19,2);",
                       grandfathered={"V8__fix_column_types.sql"}):
        failures.append("grandfathered migration was not skipped")
    if check_migration("V99__new.sql",
                       "ALTER TABLE accounts ALTER COLUMN balance TYPE numeric(19,2);",
                       grandfathered=set()) == []:
        failures.append("guard unexpectedly skipped a non-grandfathered file")

    if failures:
        print("migration-timeout self-test FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("migration-timeout self-test OK - 18 vectors.")
    return 0


def main() -> int:
    if "--self-test" in sys.argv[1:]:
        return self_test()
    if not MIGRATIONS.is_dir():
        print(f"missing migrations dir: {MIGRATIONS}", file=sys.stderr)
        return 1
    failures = []
    for sql in sorted(MIGRATIONS.glob("V*.sql")):
        try:
            text = sql.read_text(encoding="utf-8")
        except OSError as e:
            print(f"cannot read {sql.name}: {e}", file=sys.stderr)
            return 1
        failures.extend(f"{sql.name} ({issue})"
                        for issue in check_migration(sql.name, text))
    if failures:
        print("heavy migrations without timeout guards:", file=sys.stderr)
        for name in failures:
            print(f"  {name}", file=sys.stderr)
        print("Add `SET LOCAL statement_timeout = '10min';` before heavy SQL, plus",
              "`SET LOCAL lock_timeout = '30s';` before table rewrites.", file=sys.stderr)
        return 1
    print("migration timeouts ok.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
