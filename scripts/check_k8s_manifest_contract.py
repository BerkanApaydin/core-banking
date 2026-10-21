"""Fail CI when the Kubernetes manifest drifts from the application's prod contract.

The manifest is the only place a deployment is described, and the application
fails fast on several production preconditions (see
``ApplicationStartupValidator``). A manifest that omits one of them does not
fail the build — it fails the DEPLOYMENT, at 3am, as a CrashLoopBackOff.

This check compares the shipped manifest against the properties the
application requires in the ``prod`` profile and against the safety rules the
manifest itself documents: pod/container hardening (non-root, pinned
UID/GID, no service-account token, seccomp, read-only root), the
digest-or-explicit-dev-placeholder image policy, the Flyway version/digest
pin, and the DNS TCP+UDP egress needed for truncated answers.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "k8s" / "bank-app.yaml"
INGRESS = ROOT / "k8s" / "ingress.yaml"
NETWORK_POLICY = ROOT / "k8s" / "networkpolicy.yaml"
FLYWAY_JOB = ROOT / "k8s" / "flyway-migrate-job.yaml"
POM = ROOT / "pom.xml"
DOCKERFILE = ROOT / "Dockerfile"

DEV_APP_IMAGE = "bank-app:dev-local"

POD_HARDENING = (
    (r"^\s*automountServiceAccountToken:\s*false\s*$",
     "automountServiceAccountToken: false"),
    (r"^\s*runAsNonRoot:\s*true\s*$", "runAsNonRoot: true"),
    (r"^\s*runAsUser:\s*1000\s*$", "runAsUser: 1000"),
    (r"^\s*runAsGroup:\s*1000\s*$", "runAsGroup: 1000"),
    (r"^\s*fsGroup:\s*1000\s*$", "fsGroup: 1000"),
    (r"seccompProfile:\s*\n\s+type:\s*RuntimeDefault\s*$",
     "seccompProfile: RuntimeDefault"),
    (r"^\s*allowPrivilegeEscalation:\s*false\s*$",
     "allowPrivilegeEscalation: false"),
    (r"^\s*readOnlyRootFilesystem:\s*true\s*$",
     "readOnlyRootFilesystem: true"),
    (r"capabilities:\s*\n\s+drop:\s*\n\s+-\s+ALL\s*$",
     "capabilities.drop: [ALL]"),
)

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
    for path in (MANIFEST, INGRESS, NETWORK_POLICY, FLYWAY_JOB, POM, DOCKERFILE):
        if not path.is_file():
            return fail(f"{path.relative_to(ROOT)} is missing")

    manifest = MANIFEST.read_text(encoding="utf-8")
    ingress = INGRESS.read_text(encoding="utf-8")
    network_policy = NETWORK_POLICY.read_text(encoding="utf-8")
    flyway_job = FLYWAY_JOB.read_text(encoding="utf-8")
    pom = POM.read_text(encoding="utf-8")
    dockerfile = DOCKERFILE.read_text(encoding="utf-8")
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

    errors = []

    # Pod/container hardening: both workloads keep the same non-root contract;
    # runAsGroup must match the GID pinned in the Dockerfile.
    for text, source in (
        (manifest, "k8s/bank-app.yaml"),
        (flyway_job, "k8s/flyway-migrate-job.yaml"),
    ):
        for pattern, label in POD_HARDENING:
            if not re.search(pattern, text, re.MULTILINE | re.DOTALL):
                errors.append(f"{source}: missing {label}")

    if not re.search(r"addgroup\s+-S\s+-g\s+1000\s+bank", dockerfile):
        errors.append("Dockerfile: pin the image group to GID 1000 "
                      "(addgroup -S -g 1000 bank)")
    if not re.search(r"adduser\s+-S\s+-u\s+1000\s+-G\s+bank\s+bank", dockerfile):
        errors.append("Dockerfile: pin the image user to UID 1000 in group bank")

    # Image policy: the app is either the explicit local-only placeholder or
    # digest-pinned. Any other tag (bank-app:0.0.1, ghcr.io/...:v1.2.3) fails.
    app_images = re.findall(r"^\s*image:\s*(\S+)\s*$", manifest, re.MULTILINE)
    if len(app_images) != 1:
        errors.append(
            f"k8s/bank-app.yaml: expected exactly one image, found {len(app_images)}"
        )
    else:
        app_image = app_images[0]
        if app_image != DEV_APP_IMAGE and "@sha256:" not in app_image:
            errors.append(
                "k8s/bank-app.yaml: image must be the explicit local placeholder "
                f"{DEV_APP_IMAGE!r} or a digest-pinned reference, found {app_image!r}"
            )

    # Flyway Job: version must track pom.xml and the reference must carry the
    # multi-arch index digest. TTL is forbidden because a fixed Job name plus
    # automatic deletion races the next release; the header must document the
    # delete-before-apply step.
    flyway_images = re.findall(r"^\s*image:\s*(\S+)\s*$", flyway_job, re.MULTILINE)
    if len(flyway_images) != 1:
        errors.append(
            "k8s/flyway-migrate-job.yaml: expected exactly one image, "
            f"found {len(flyway_images)}"
        )
    else:
        flyway_image = flyway_images[0]
        flyway_version = re.search(r"<flyway\.version>([^<]+)</flyway\.version>", pom)
        if not flyway_version:
            errors.append("pom.xml: <flyway.version> is missing")
        else:
            expected = (
                rf"^flyway/flyway:{re.escape(flyway_version.group(1))}-alpine"
                r"@sha256:[0-9a-f]{64}$"
            )
            if not re.fullmatch(expected, flyway_image):
                errors.append(
                    "k8s/flyway-migrate-job.yaml: image must be "
                    f"flyway/flyway:{flyway_version.group(1)}-alpine"
                    "@sha256:<64 hex>, "
                    f"found {flyway_image!r}"
                )
    if re.search(r"^\s*ttlSecondsAfterFinished:", flyway_job, re.MULTILINE):
        errors.append(
            "k8s/flyway-migrate-job.yaml: ttlSecondsAfterFinished is forbidden "
            "(fixed name + immutable spec; delete the previous Job before apply)"
        )
    if "kubectl delete job bank-flyway-migrate --ignore-not-found" not in flyway_job:
        errors.append(
            "k8s/flyway-migrate-job.yaml: document the delete-before-apply step"
        )
    if "lock_timeout" not in flyway_job:
        errors.append(
            "k8s/flyway-migrate-job.yaml: set lock_timeout in the JDBC URL "
            "(protects the grandfathered V28/V32/V46 rewrites)"
        )

    # DNS over TCP is mandatory for truncated UDP responses; a UDP-only rule
    # turns large DNS answers into intermittent resolution failures.
    dns_block = re.search(
        r"# Cluster DNS\.(.*?)# PostgreSQL\.", network_policy, re.DOTALL
    )
    if not dns_block:
        errors.append(
            "k8s/networkpolicy.yaml: cannot locate the Cluster DNS egress block"
        )
    else:
        block = dns_block.group(1)
        for protocol in ("UDP", "TCP"):
            if not re.search(rf"protocol:\s*{protocol}\s*\n\s*port:\s*53", block):
                errors.append(
                    f"k8s/networkpolicy.yaml: DNS egress must allow {protocol} 53"
                )

    if errors:
        return fail("hardening/image policy drift:\n  - " + "\n  - ".join(errors))

    print(
        "k8s manifest contract OK: "
        f"{len(env)} env vars, app image={DEV_APP_IMAGE!r} or digest, "
        f"Flyway pinned to pom.xml, hardening+TCP-DNS present, "
        f"CORS={cors!r}, ingress hosts={sorted(hosts)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
