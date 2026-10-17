"""Dependency-free static-analysis gate (audit follow-up).

The build has no Checkstyle/SpotBugs/PMD plugin (and the offline build cache
cannot resolve new Maven plugins), so structural hotspots are pinned here
instead — same CI step as the other check_*.py gates:

1. Long methods: no main-source method body longer than MAX_METHOD_LINES.
   (Motivation: JwtAuthenticationFilter.doFilterInternal grew to ~133 lines
   with five interleaved decisions before it was split into named predicates;
   LoginUserUseCaseImpl/RefreshSessionUseCaseImpl got the same treatment.)
2. Config-knob literals: BCrypt cost, HSTS max-age and cache bounds must come
   from @ConfigurationProperties records, never inline literals in the
   adapters that consume them.
3. No System.out/err or printStackTrace in main sources (logging only).
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["account", "user", "transfer", "audit", "common", "infrastructure",
           "persistence", "app", "account-api"]

# Bodies above this fail the gate. Measured 2026-10: the longest production
# methods are validateProductionConfig (~88, a sequential require* checklist),
# the refresh-rotation helpers (~50) and the outbox wiring (~56).
MAX_METHOD_LINES = 90

# (file suffix, forbidden regex, why) — literals that must live in
# @ConfigurationProperties records instead.
LITERAL_RULES = [
    ("SecurityConfig.java", r"new\s+BCryptPasswordEncoder\s*\(\s*\d+",
     "BCrypt cost must come from SecurityProperties.passwordEncoderStrength"),
    ("SecurityConfig.java", r"maxAgeInSeconds\s*\(\s*\d+",
     "HSTS max-age must come from SecurityProperties.hstsMaxAgeSeconds"),
    ("CaffeineRateLimiter.java", r"maximumSize\s*\(\s*\d+",
     "cache bound must come from RateLimitProperties.cacheMaxSize"),
    ("CaffeineLoginAttemptAdapter.java", r"maximumSize\s*\(\s*\d+",
     "cache bound must come from LoginAttemptProperties.cacheMaxSize"),
]

PRINT_RE = re.compile(r"System\.(out|err)\.print|\.printStackTrace\s*\(")


def main_files():
    for module in MODULES:
        for java in (ROOT / module).rglob("*.java"):
            rel = str(java.relative_to(ROOT)).replace("\\", "/")
            if "/test/" in rel or "/target/" in rel:
                continue
            yield java


def method_lengths(text):
    """Yield (signature, body_lines) using brace-depth tracking."""
    lines = text.splitlines()
    depth = 0
    start = -1
    sig = ""
    for i, line in enumerate(lines):
        stripped = line.strip()
        if depth == 1 and start == -1 and "(" in line and line.rstrip().endswith("{") \
                and not re.match(r"^(if|for|while|switch|catch|synchronized|try|do)\b", stripped) \
                and not stripped.startswith("new ") and "=>" not in line \
                and "class " not in stripped and "interface " not in stripped \
                and "enum " not in stripped and "@" not in stripped:
            start = i
            sig = stripped[:100]
        depth += line.count("{") - line.count("}")
        if start != -1 and depth <= 1 and "}" in line:
            yield sig, i - start + 1
            start = -1
        if depth < 1:
            depth = 0
            start = -1


def main() -> int:
    failures = []
    for java in main_files():
        rel = str(java.relative_to(ROOT)).replace("\\", "/")
        try:
            text = java.read_text(encoding="utf-8")
        except OSError as e:
            print(f"cannot read {rel}: {e}", file=sys.stderr)
            return 1
        for sig, length in method_lengths(text):
            if length > MAX_METHOD_LINES:
                failures.append(f"{rel}: method over {MAX_METHOD_LINES} lines ({length}): {sig}")
        for suffix, pattern, why in LITERAL_RULES:
            if java.name == suffix and re.search(pattern, text):
                failures.append(f"{rel}: {why}")
        if PRINT_RE.search(text):
            failures.append(f"{rel}: System.out/err or printStackTrace in main sources (use logging)")
    if failures:
        print("static-analysis gate failures:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1
    print(f"static analysis ok (methods <= {MAX_METHOD_LINES} lines, no config literals, no stdout).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
