"""Fail CI when the compose JWT wiring drifts from the fail-closed contract.

Contract (zero-setup local boot + no prod escape):
1. `docker compose up` works with no env vars: JWT_SECRET has a :-default.
2. The compose default EQUALS JwtTokenProvider.DEFAULT_JWT_SECRET, so prod
   refuses exactly this value twice (JwtTokenProvider with the prod-yml
   `allow-default-secret: false` literal, plus ApplicationStartupValidator).
   A different static default would boot prod with a publicly-known key.
3. All published ports are loopback-bound (127.0.0.1), so the publicly-known
   dev default is unreachable from the network.
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
    print(f"compose jwt contract violation: {message}", file=sys.stderr)
    return 1


def main() -> int:
    compose = COMPOSE.read_text(encoding="utf-8")
    match = re.search(r"JWT_SECRET:\s*\"\$\{JWT_SECRET:-([^}]+)\}\"", compose)
    if not match:
        return fail("docker-compose.yml must define a JWT_SECRET :-default "
                    "(one-shot local boot without command-line secrets)")
    compose_default = match.group(1)

    provider = PROVIDER.read_text(encoding="utf-8")
    constant = re.search(r'DEFAULT_JWT_SECRET\s*=\s*"([^"]+)"', provider)
    if not constant:
        return fail("JwtTokenProvider.DEFAULT_JWT_SECRET literal not found")
    if compose_default != constant.group(1):
        return fail("compose default must equal JwtTokenProvider.DEFAULT_JWT_SECRET "
                    "so prod fail-closed validation catches exactly this value")

    for port_match in re.finditer(r'^\s*-\s*"([^"]+:[0-9]+:[0-9]+)"\s*$', compose, re.MULTILINE):
        mapping = port_match.group(1)
        if not mapping.startswith("127.0.0.1:"):
            return fail(f"published port must be loopback-bound (127.0.0.1:...), got: {mapping}")

    prod = PROD_YML.read_text(encoding="utf-8")
    if not re.search(r"^\s*allow-default-secret:\s*false\s*$", prod, re.MULTILINE):
        return fail("application-prod.yml must keep allow-default-secret: false as a literal")
    print("compose JWT contract ok: dev default matches code constant, "
          "loopback-only ports, prod refuses default.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
