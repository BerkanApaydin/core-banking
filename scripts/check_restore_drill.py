"""CI guard for the restore-drill contract (no staging database needed).

Fails when the drill script, its record template, or the restored-database
verifier drift apart:

1. ops/restore-drill.sh must be syntactically valid bash (bash -n).
2. The drill must keep both phases: a persisted pre-backup baseline and a
   --verify phase that compares the restored database against it.
3. The baseline/verify data collection must fail closed (no ``|| true`` around
   psql/health checks) and must use ``ON_ERROR_STOP``.
4. The ledger net-zero sign convention in the drill script must match the
   production gauge query (BacklogMetricsReporter.LEDGER_NONZERO_SQL):
   CREDIT-positive in both, otherwise the drill could certify a broken
   ledger (or page on a healthy one).
5. The record template must contain every field the runbook requires
   (RPO/RTO verdicts, gaps-as-issues, image digest, flyway version, both
   snapshot paths, automated verification).
6. Every helper the drill shells out to must exist
   (ops/health_smoke.py + its unit tests, ops/restore_verify.py + self-test).
"""

import os

from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
DRILL = ROOT / "ops" / "restore-drill.sh"
TEMPLATE = ROOT / "docs" / "drills" / "restore-drill-record-template.md"
VERIFIER = ROOT / "ops" / "restore_verify.py"
REPORTER = (
    ROOT
    / "infrastructure"
    / "src" / "main" / "java" / "com" / "bank" / "app"
    / "infrastructure" / "adapter" / "in" / "metrics"
    / "BacklogMetricsReporter.java"
)
REQUIRED_TEMPLATE_FIELDS = [
    "date_utc",
    "staging_target",
    "restored_target",
    "image_digest",
    "git_sha",
    "flyway_version",
    "backup_method",
    "baseline_snapshot",
    "restored_snapshot",
    "ledger reconcile",
    "totals reconcile",
    "smoke check",
    "automated_verification",
    "RPO",
    "RTO",
    "gaps filed as issues",
]


def fail(message: str) -> int:
    print(f"restore-drill contract mismatch: {message}", file=sys.stderr)
    return 1


def find_bash() -> Path | None:
    if os.name == "nt":
        candidates = (
            Path(r"C:\Program Files\Git\bin\bash.exe"),
            Path(r"C:\Program Files\Git\usr\bin\bash.exe"),
            Path(r"C:\Program Files (x86)\Git\bin\bash.exe"),
        )
        for candidate in candidates:
            if candidate.is_file():
                return candidate
    found = shutil.which("bash")
    return Path(found) if found else None


def check_bash_syntax(drill: Path) -> str | None:
    bash = find_bash()
    if bash is None:
        print("warning: no bash found, skipping shell syntax check", file=sys.stderr)
        return None
    try:
        probe = subprocess.run([str(bash), "--version"], capture_output=True, text=True)
        usable = probe.returncode == 0
    except OSError:
        usable = False
    # Windows dev machines may ship a broken WSL relay shim; the syntax gate is
    # enforced on Linux CI (or by Git Bash when it is installed).
    if not usable:
        print(f"warning: {bash} is not usable, skipping shell syntax check", file=sys.stderr)
        return None
    proc = subprocess.run([str(bash), "-n", str(drill)], capture_output=True, text=True)
    if proc.returncode != 0:
        return f"ops/restore-drill.sh fails bash -n: {proc.stderr.strip()}"
    return None


def main() -> int:
    if not DRILL.is_file():
        return fail("ops/restore-drill.sh is missing")
    syntax_error = check_bash_syntax(DRILL)
    if syntax_error:
        return fail(syntax_error)

    drill = DRILL.read_text(encoding="utf-8")
    if "|| true" in drill:
        return fail(
            "drill still swallows failures with '|| true'; baseline/verification "
            "checks must fail closed"
        )
    for token in (
        "ON_ERROR_STOP=1",
        "--verify",
        "baseline_snapshot",
        "restored_snapshot",
        "restore_verify.py",
        "automated_verification",
    ):
        if token not in drill:
            return fail(f"drill lost required verification token: {token}")

    drill_flat = re.sub(r'[\s"+]+', "", drill)
    if "direction='CREDIT'THENamountELSE-amount" not in drill_flat:
        return fail("drill ledger reconcile query lost the CREDIT-positive convention")

    if not REPORTER.is_file():
        return fail("BacklogMetricsReporter.java is missing")
    reporter_flat = re.sub(r'[\s"+]+', "", REPORTER.read_text(encoding="utf-8"))
    if "direction='CREDIT'THENamountELSE-amount" not in reporter_flat:
        return fail("BacklogMetricsReporter ledger gauge lost the CREDIT-positive convention")

    if not VERIFIER.is_file():
        return fail("ops/restore_verify.py is missing")
    verifier = VERIFIER.read_text(encoding="utf-8")
    for token in (
        "outbox_pending",
        "outbox_processed",
        "token_revocations",
        "refresh_tokens",
        "ledger_nonzero_refs",
        "flyway_version",
    ):
        if token not in drill:
            return fail(f"drill lost required snapshot field: {token}")
        if token not in verifier:
            return fail(f"restore verifier lost required snapshot field: {token}")
    proc = subprocess.run(
        [sys.executable, str(VERIFIER), "--self-test"],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        return fail(
            "ops/restore_verify.py --self-test failed: "
            f"{proc.stdout.strip()} {proc.stderr.strip()}".strip()
        )

    if not TEMPLATE.is_file():
        return fail("docs/drills/restore-drill-record-template.md is missing")
    template = TEMPLATE.read_text(encoding="utf-8").lower()
    for field in REQUIRED_TEMPLATE_FIELDS:
        if field.lower() not in template:
            return fail(f"record template lost required field: {field}")

    for helper in ("ops/health_smoke.py", "ops/test_health_smoke.py"):
        if not (ROOT / helper).is_file():
            return fail(f"drill helper is missing: {helper}")

    print(
        "Restore-drill contract matches the two-phase drill, verifier, "
        "template and ledger gauge."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
