"""Fail CI when the compose JWT default drifts from the code constant.

Single-command `docker compose up` works because the compose default equals
JwtTokenProvider.DEFAULT_JWT_SECRET: dev boots with it (WARN logged), while
prod refuses exactly this value twice (JwtTokenProvider with the prod-yml
`allow-default-secret: false` literal, plus ApplicationStartupValidator).
If the two literals diverge, a forgotten JWT_SECRET in a prod-shaped
deployment would boot with a publicly known key instead of failing fast.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMPOSE = ROOT / "docker-compose.yml"
PROVIDER = (
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
    / "out"
    / "security"
    / "JwtTokenProvider.java"
)
PROD_YML = ROOT / "app" / "src" / "main" / "resources" / "application-prod.yml"


def fail(message: str) -> int:
    print(f"compose jwt default mismatch: {message}", file=sys.stderr)
    return 1


def main() -> int:
    compose = COMPOSE.read_text(encoding="utf-8")
    match = re.search(r"JWT_SECRET:\s*\"\$\{JWT_SECRET:-([^}]+)\}\"", compose)
    if not match:
        return fail("docker-compose.yml has no JWT_SECRET :-default (one-shot local boot is broken)")
    compose_default = match.group(1)

    provider = PROVIDER.read_text(encoding="utf-8")
    constant = re.search(r'DEFAULT_JWT_SECRET\s*=\s*"([^"]+)"', provider)
    if not constant:
        return fail("JwtTokenProvider.DEFAULT_JWT_SECRET literal not found")
    if compose_default != constant.group(1):
        return fail("compose default must equal JwtTokenProvider.DEFAULT_JWT_SECRET")

    prod = PROD_YML.read_text(encoding="utf-8")
    if not re.search(r"^\s*allow-default-secret:\s*false\s*$", prod, re.MULTILINE):
        return fail("application-prod.yml must keep allow-default-secret: false as a literal")
    print("compose JWT default matches the code constant; prod refuses it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
