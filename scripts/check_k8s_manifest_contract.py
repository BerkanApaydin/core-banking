"""Fail CI when the Kubernetes manifest drifts from the application's prod contract.

The manifest is the only place a deployment is described, and the application
fails fast on several production preconditions (see
``ApplicationStartupValidator``). A manifest that omits one of them does not
fail the build — it fails the DEPLOYMENT, at 3am, as a CrashLoopBackOff.

This check compares the shipped manifest against the properties the
application requires in the ``prod`` profile and against the safety rules the
manifest itself documents.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "k8s" / "bank-app.yaml"
INGRESS = ROOT / "k8s" / "ingress.yaml"

# Environment variables the prod profile REQUIRES. The application refuses to
# boot without them, so a manifest that does not set them deploys a pod that
# cannot start.
REQUIRED_ENV = (
    "SPRING_PROFILES_ACTIVE",
    "DB_HOST",
    "DB_PORT",
    "DB_USERNAME",
    "DB_PASSWORD",
    "JWT_SECRET",
    "MFA_ENCRYPTION_KEY",
    "REDIS_HOST",
    "REDIS_PORT",
    "CORS_ALLOWED_ORIGINS",
    "DB_MAX_POOL_SIZE",
)

# Values that must never reach production.
LOOPBACK_ORIGINS = ("localhost", "127.0.0.1", "[::1]", "::1")

# prod requires a shared Redis backend for the rate limiter and the login
# guard: a per-JVM limiter multiplies the brute-force budget per replica.
# These are pinned by the PROD PROFILE (application-prod.yml), not by the
# manifest — the manifest only has to not contradict them.
REQUIRED_SHARED_BACKENDS = (
    "app.security.rate-limit.backend",
    "app.security.failed-login.backend",
)


def fail(message: str) -> int:
    print(f"FAIL: {message}", file=sys.stderr)
    return 1


def env_entries(text: str) -> dict[str, str]:
    """Maps env var name -> literal value (empty for secret references)."""
    entries: dict[str, str] = {}
    pattern = re.compile(
        r"-\s+name:\s*(?P<name>[A-Z0-9_]+)\s*\n"
        r"(?:\s+value:\s*(?P<value>[^\n#]+)\n)?"
        r"(?:\s+valueFrom:\s*\n(?:\s+.*\n)*?\s+key:\s*[^\n]+\n"
        r"(?:\s+optional:\s*[^\n]+\n)?)?"
    )
    for match in pattern.finditer(text):
        value = match.group("value")
        entries[match.group("name")] = value.strip().strip('"').strip("'") if value else ""
    return entries


def prod_yaml(text: str) -> dict[str, str]:
    """Flattens a nested YAML mapping into dotted property keys.

    A minimal indentation-based reader: the CI image has no PyYAML, and the
    prod profile only uses nested mappings, scalars and lists. List items are
    skipped (their value is not a scalar property).
    """
    flat: dict[str, str] = {}
    stack: list[tuple[int, str]] = []

    for raw in text.splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        stripped = line.strip()
        if stripped.startswith("- "):
            # A list item: it has no scalar property value.
            continue
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        while stack and stack[-1][0] >= indent:
            stack.pop()
        prefix = ".".join(name for _, name in stack)
        if value:
            flat[f"{prefix}.{key}" if prefix else key] = value
        else:
            stack.append((indent, key))
    return flat


def main() -> int:
    if not MANIFEST.is_file():
        return fail(f"{MANIFEST.relative_to(ROOT)} is missing")
    if not INGRESS.is_file():
        return fail(f"{INGRESS.relative_to(ROOT)} is missing")

    manifest = MANIFEST.read_text(encoding="utf-8")
    ingress = INGRESS.read_text(encoding="utf-8")
    env = env_entries(manifest)

    prod_profile_path = ROOT / "app" / "src" / "main" / "resources" / "application-prod.yml"
    if not prod_profile_path.is_file():
        return fail(f"{prod_profile_path.relative_to(ROOT)} is missing")
    prod_profile = prod_yaml(prod_profile_path.read_text(encoding="utf-8"))

    missing = [name for name in REQUIRED_ENV if name not in env]
    if missing:
        return fail(
            "k8s/bank-app.yaml does not set the environment the prod profile "
            f"requires: {', '.join(sorted(missing))}"
        )

    if env.get("SPRING_PROFILES_ACTIVE") != "prod":
        return fail(
            "k8s/bank-app.yaml must pin SPRING_PROFILES_ACTIVE=prod (found: "
            f"{env.get('SPRING_PROFILES_ACTIVE')!r})"
        )

    # CORS: the prod validator rejects any loopback origin, so a manifest
    # that leaves the default in place deploys a pod that cannot start.
    cors = env.get("CORS_ALLOWED_ORIGINS", "")
    if not cors:
        return fail("k8s/bank-app.yaml sets an empty CORS_ALLOWED_ORIGINS")
    for origin in cors.split(","):
        origin = origin.strip()
        if not origin:
            continue
        if any(loopback in origin for loopback in LOOPBACK_ORIGINS):
            return fail(
                f"k8s/bank-app.yaml sets a loopback CORS origin ({origin!r}); "
                "the prod validator refuses to boot with one"
            )

    # The CORS value must match the ingress host, otherwise the browser
    # rejects every credentialed cross-origin call.
    hosts = set(re.findall(r"^\s*-\s*host:\s*(\S+)\s*$", ingress, re.MULTILINE))
    hosts |= set(re.findall(r"-\s*hosts:\s*\n\s*-\s*(\S+)", ingress))
    for origin in cors.split(","):
        origin = origin.strip()
        if not origin:
            continue
        host = origin.split("://", 1)[-1].split("/", 1)[0].split(":", 1)[0]
        if hosts and host not in hosts:
            return fail(
                f"CORS origin {origin!r} does not match any ingress host "
                f"({', '.join(sorted(hosts))})"
            )

    for name in REQUIRED_SHARED_BACKENDS:
        if name not in prod_profile:
            return fail(
                f"application-prod.yml must pin {name}: a per-JVM backend "
                "multiplies the security budget per replica"
            )
        value = prod_profile[name]
        if "redis" not in value:
            return fail(
                f"application-prod.yml must pin {name} to redis (found: {value!r})"
            )

    print(
        "k8s manifest contract OK: "
        f"{len(env)} env vars, CORS={cors!r}, ingress hosts={sorted(hosts)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
