"""CI guard for the restore-drill contract (no staging database needed).

Fails when the drill script, its record template, or the ledger invariant
queries drift apart:

1. ops/restore-drill.sh must be syntactically valid bash (bash -n).
2. The ledger net-zero sign convention in the drill script must match the
   production gauge query (BacklogMetricsReporter.LEDGER_NONZERO_SQL):
   CREDIT-positive in both, otherwise the drill could certify a broken
   ledger (or page on a healthy one).
3. The record template must contain every field the runbook requires
   (RPO/RTO verdicts, gaps-as-issues, image digest, flyway version).
4. Every helper the drill script shells out to must exist
   (ops/health_smoke.py + its unit tests).
"""

import shutil

from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
DRILL = ROOT / "ops" / "restore-drill.sh"
TEMPLATE = ROOT / "docs" / "drills" / "restore-drill-record-template.md"
REPORTER = (
    ROOT
    / "infrastructure"
    / "src" / "main" / "java" / "com" / "bank" / "app"
    / "infrastructure" / "adapter" / "out" / "metrics"
    / "BacklogMetricsReporter.java"
)
REQUIRED_TEMPLATE_FIELDS = [
    "date_utc",
    "staging_target",
    "image_digest",
    "git_sha",
    "flyway_version",
    "backup_method",
    "ledger reconcile",
    "totals reconcile",
    "smoke check",
    "RPO",
    "RTO",
    "gaps filed as issues",
]


def fail(message: str) -> int:
    print(f"restore-drill contract mismatch: {message}", file=sys.stderr)
    return 1


def main() -> int:
    if not DRILL.is_file():
        return fail("ops/restore-drill.sh is missing")
    bash = shutil.which("bash") or shutil.which("C:/Program Files/Git/bin/bash.exe")
    if bash is None:
        print("warning: no bash found, skipping shell syntax check", file=sys.stderr)
    else:
        try:
            probe = subprocess.run([bash, "--version"], capture_output=True, text=True)
            usable = probe.returncode == 0
        except OSError:
            usable = False
        # Windows dev machines ship broken bash shims (WSL relay without a
        # distro): the syntax gate is enforced on Linux CI instead.
        if not usable:
            print(f"warning: {bash} is not usable, skipping shell syntax check", file=sys.stderr)
        else:
            proc = subprocess.run([bash, "-n", str(DRILL)], capture_output=True, text=True)
            if proc.returncode != 0:
                return fail(f"ops/restore-drill.sh fails bash -n: {proc.stderr.strip()}")

    drill_flat = re.sub(r'[\s"+]+', "", DRILL.read_text(encoding="utf-8"))
    if "direction='CREDIT'THENamountELSE-amount" not in drill_flat:
        return fail("drill ledger reconcile query lost the CREDIT-positive convention")

    if not REPORTER.is_file():
        return fail("BacklogMetricsReporter.java is missing")
    reporter_flat = re.sub(r'[\s"+]+', "", REPORTER.read_text(encoding="utf-8"))
    if "direction='CREDIT'THENamountELSE-amount" not in reporter_flat:
        return fail("BacklogMetricsReporter ledger gauge lost the CREDIT-positive convention")

    if not TEMPLATE.is_file():
        return fail("docs/drills/restore-drill-record-template.md is missing")
    template = TEMPLATE.read_text(encoding="utf-8").lower()
    for field in REQUIRED_TEMPLATE_FIELDS:
        if field.lower() not in template:
            return fail(f"record template lost required field: {field}")

    for helper in ("ops/health_smoke.py", "ops/test_health_smoke.py"):
        if not (ROOT / helper).is_file():
            return fail(f"drill helper is missing: {helper}")

    print("Restore-drill contract matches the drill script, template and ledger gauge.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
