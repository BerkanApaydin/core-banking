"""Fail CI when load_tests/acceptance.yml drifts from the runner implementation."""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ACCEPTANCE = ROOT / "load_tests" / "acceptance.yml"
RUNNER = ROOT / "load_tests" / "runner.py"


def fail(message: str) -> int:
    print(f"load acceptance mismatch: {message}", file=sys.stderr)
    return 1


def main() -> int:
    text = ACCEPTANCE.read_text(encoding="utf-8")
    runner = RUNNER.read_text(encoding="utf-8")

    def value(key: str) -> str:
        match = re.search(rf"^\s*{key}:\s*(.+?)\s*$", text, re.MULTILINE)
        if not match:
            print(f"load acceptance mismatch: missing key {key}", file=sys.stderr)
            raise SystemExit(1)
        return match.group(1).strip()

    if "CAPACITY_TEST_TARGET_LATENCY_MS    = 200.0" not in runner:
        return fail("runner CAPACITY_TEST_TARGET_LATENCY_MS changed")
    if value("target_p95_ms") != "200.0":
        return fail("acceptance target_p95_ms must be 200.0")

    if "success >= 99%" not in runner:
        return fail("runner capacity success gate changed")
    if value("min_success_pct") != "99.0":
        return fail("acceptance min_success_pct must be 99.0")

    if "for i in range(1, 16):" not in runner:
        return fail("runner rate-limit request count changed")
    if value("requests") != "15":
        return fail("acceptance rate_limit.requests must be 15")

    if 'time.sleep(11)' not in runner:
        return fail("runner rate-limit cooldown changed")
    if value("cooldown_seconds") != "11":
        return fail("acceptance cooldown_seconds must be 11")

    rate_limit_props = (
        ROOT
        / "infrastructure"
        / "src"
        / "main"
        / "java"
        / "com"
        / "bank"
        / "app"
        / "infrastructure"
        / "adapter"
        / "in"
        / "web"
        / "RateLimitProperties.java"
    ).read_text(encoding="utf-8")
    if '@DefaultValue("10") int maxRequests' not in rate_limit_props:
        return fail("RateLimitProperties default maxRequests changed")
    if value("limit_per_window") != "10":
        return fail("acceptance limit_per_window must be 10")
    if '@DefaultValue("10000") int timeWindowMs' not in rate_limit_props:
        return fail("RateLimitProperties default timeWindowMs changed")
    if value("window_seconds") != "10":
        return fail("acceptance window_seconds must be 10")
    # Auth-tier smoke above pins the tight tier; the resource tier must stay
    # looser so dashboard-style reads never share the login budget.
    if '@DefaultValue("120") int resourceMaxRequests' not in rate_limit_props:
        return fail("RateLimitProperties default resourceMaxRequests changed")
    if '@DefaultValue("60000") int resourceTimeWindowMs' not in rate_limit_props:
        return fail("RateLimitProperties default resourceTimeWindowMs changed")

    print("Load acceptance thresholds match the runner implementation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
