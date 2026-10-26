#!/usr/bin/env python3
"""Static repository contracts shared by CI and the local test launcher.

These guards used to live only as inline greps in ``.github/workflows/ci.yml``.
Keeping them in one executable lets ``scripts/test-all.sh`` / ``.ps1`` run the
same checks locally instead of silently omitting CI-only gates.

Usage: python3 scripts/check_ci_contracts.py
Exit 0 when every static contract holds, 1 with ``::error::`` annotations
otherwise (the annotation prefix is understood by both GitHub Actions and a
plain terminal).
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
CI = ROOT / ".github" / "workflows" / "ci.yml"
RELEASE = ROOT / ".github" / "workflows" / "release.yml"
MANIFEST = ROOT / "k8s" / "bank-app.yaml"
MIGRATIONS = ROOT / "migrations" / "src" / "main" / "resources" / "db" / "migration"


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise FileNotFoundError(rel)
    return path.read_text(encoding="utf-8")


def problems_for_mutable_image_tag() -> list[str]:
    text = read("k8s/bank-app.yaml")
    problems: list[str] = []
    if re.search(
        r"(?m)^[ \t]*image:[ \t]+bank-app:latest(?:[ \t#]|$)", text
    ):
        problems.append(
            "k8s/bank-app.yaml must not reference bank-app:latest "
            "(use the explicit dev-local placeholder or a digest; K9/D7)"
        )
    images = re.findall(r"(?m)^[ \t]*image:[ \t]+(\S+)", text)
    if len(images) != 1:
        problems.append(
            f"k8s/bank-app.yaml must declare exactly one image, found {len(images)}"
        )
    elif images[0] != "bank-app:dev-local" and "@sha256:" not in images[0]:
        problems.append(
            "k8s/bank-app.yaml app image must be bank-app:dev-local or a "
            f"digest-pinned reference, found {images[0]!r}"
        )
    return problems


def problems_for_image_pin_encoding() -> list[str]:
    """Windows PowerShell 5.1 must not see a BOM or ANSI-damaged literal."""
    path = ROOT / "scripts" / "pin-image-digest.ps1"
    if not path.is_file():
        return ["scripts/pin-image-digest.ps1 is missing"]
    data = path.read_bytes()
    problems: list[str] = []
    if data[:3] == b"\xef\xbb\xbf":
        problems.append("scripts/pin-image-digest.ps1 must stay UTF-8 without a BOM")
    try:
        data.decode("ascii")
    except UnicodeDecodeError as exc:
        problems.append(
            "scripts/pin-image-digest.ps1 must stay ASCII-only: PowerShell 5.1 "
            "parses a BOM-less .ps1 with the ANSI code page, so non-ASCII "
            f"literals are corrupted before the encoding test runs (byte {exc.start})"
        )
        return problems
    text = data.decode("ascii")
    for token in (
        "UTF8Encoding($false)",
        "[System.IO.File]::ReadAllText",
        "[System.IO.File]::WriteAllText",
        "-SelfTest",
    ):
        if token not in text:
            problems.append(
                f"scripts/pin-image-digest.ps1 lost the UTF-8/self-test guard {token!r}"
            )
    return problems


def problems_for_migration_soak() -> list[str]:
    problems: list[str] = []
    required = {
        "scripts/migration_soak.sh": (
            "BASELINE_TARGET",
            "MAX_LOCK_WAITS",
            "migration soak exceeded the lock-wait budget",
        ),
        "migrations/src/main/resources/db/migration/"
        "V43__transfer_history_keyset_covering_indexes.sql": (),
        "k8s/grafana-dashboard.json": (),
    }
    for rel, tokens in required.items():
        path = ROOT / rel
        if not path.is_file():
            problems.append(f"migration-soak prerequisite is missing: {rel}")
            continue
        if tokens:
            text = path.read_text(encoding="utf-8")
            for token in tokens:
                if token not in text:
                    problems.append(
                        f"migration-soak guard token {token!r} lost from {rel}"
                    )

    ci_text = read(".github/workflows/ci.yml")
    if not re.search(r"(?m)^  migration-soak:\s*$", ci_text):
        problems.append(
            ".github/workflows/ci.yml lost the migration-soak job "
            "(O-1/DB-1 large-table guard)"
        )
    return problems


def problems_for_migration_content() -> list[str]:
    problems: list[str] = []
    try:
        v27 = read(
            "migrations/src/main/resources/db/migration/"
            "V27__index_pending_http_idempotency.sql"
        )
        v51 = read(
            "migrations/src/main/resources/db/migration/"
            "V51__drop_orphaned_v27_partial.sql"
        )
        v52 = read(
            "migrations/src/main/resources/db/migration/"
            "V52__drop_subsumed_indexes_and_retention_expression_index.sql"
        )
        v53 = read(
            "migrations/src/main/resources/db/migration/"
            "V53__widen_idempotency_response_body.sql"
        )
    except FileNotFoundError as exc:
        return [f"migration content contract file is missing: {exc}"]

    real_index = "idx_idempotency_pending_http_created_at"
    if real_index not in v27:
        problems.append(f"V27 lost the real index name {real_index}")
    if real_index not in v51:
        problems.append(f"V51 must drop the real V27 index name {real_index}")
    if re.search(r"DROP INDEX.*idx_pending_http_idempotency", v51):
        problems.append("V51 repeats the V41 phantom index name")

    subsumed = (
        "idx_outbox_events_unprocessed",
        "idx_transfers_created_at",
        "idx_audit_logs_timestamp",
        "idx_ledger_account_business",
        "idx_accounts_user_id_status",
        "idx_audit_logs_username",
        "idx_audit_logs_username_action",
        "idx_idempotency_keys_created_at",
    )
    for index in subsumed:
        if f"DROP INDEX IF EXISTS {index}" not in v52:
            problems.append(f"V52 lost the drop for subsumed index {index}")
    if "idx_outbox_retention_processed_created" not in v52:
        problems.append("V52 lost the retention expression index")
    if "COALESCE(processed_at, created_at)" not in v52:
        problems.append("V52 retention index must use COALESCE(processed_at, created_at)")
    if (
        "ALTER TABLE idempotency_keys ALTER COLUMN response_body TYPE TEXT"
        not in v53
    ):
        problems.append("V53 must widen idempotency_keys.response_body to TEXT")
    return problems


def problems_for_supply_chain() -> list[str]:
    problems: list[str] = []
    try:
        release = read(".github/workflows/release.yml")
        ci = read(".github/workflows/ci.yml")
    except FileNotFoundError as exc:
        return [f"supply-chain workflow is missing: {exc}"]

    release_tokens = (
        "verify-green-ci",
        "needs: verify-green-ci",
        "BASE_BUILDER_IMAGE",
        "Validate release tag points at this commit",
        "refs/tags/${TAG}",
        "Resolve base images to immutable digests",
        "provenance: true",
        "FLYWAY_VERSION",
        "docker buildx imagetools inspect",
        "flyway/flyway:${FLYWAY_VERSION}-alpine",
    )
    for token in release_tokens:
        if token not in release:
            problems.append(f"release.yml lost supply-chain contract token {token!r}")
    # The workflow compares the checked-out SHA with the tag's commit. Match
    # the comparison itself, not a brittle quoting variant: the old inline grep
    # looked for `TAG_SHA != "$SHA"` and never matched `"$TAG_SHA" != "$SHA"`.
    if not re.search(r"TAG_SHA.*!=.*\$SHA", release):
        problems.append("release.yml lost the tag-SHA equality guard")
    # Trivy image scanning was removed on 2026-10-23: it only failed on the
    # accepted-risk spring-webmvc advisories already tracked in
    # scripts/dependency_allowlist.json. OSV + dependency-review remain the
    # blocking library gates; do not re-add a hard-fail image scan here without
    # an allowlist-aware mechanism.
    for workflow in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
        for match in re.finditer(
            r"(?m)^\s*uses:\s*(\S+)",
            workflow.read_text(encoding="utf-8"),
        ):
            ref = match.group(1)
            if ref.startswith(("./", "docker://")):
                continue
            if not re.search(r"@[0-9a-f]{40}$", ref):
                problems.append(
                    f"{workflow.name} uses a mutable action ref {ref!r}; "
                    "pin a full commit SHA"
                )
    try:
        manifest_contract = read("scripts/check_k8s_manifest_contract.py")
    except FileNotFoundError as exc:
        return [f"manifest contract script is missing: {exc}"]
    if "DEV_APP_IMAGE" not in manifest_contract:
        problems.append(
            "scripts/check_k8s_manifest_contract.py lost the dev-local/digest "
            "image policy"
        )
    return problems


def problems_for_deployment_contracts() -> list[str]:
    problems: list[str] = []
    try:
        manifest = read("k8s/bank-app.yaml")
    except FileNotFoundError as exc:
        return [f"deployment manifest is missing: {exc}"]
    for kind in ("HorizontalPodAutoscaler", "PodDisruptionBudget"):
        if f"kind: {kind}" not in manifest:
            problems.append(f"k8s/bank-app.yaml lost kind: {kind}")
    for rel in (
        "docker-compose.prod.yml",
        "scripts/audit_partition_cutover.sql",
        "scripts/ensure_audit_partition.sql",
        "scripts/audit_partition_finalize.sql",
    ):
        if not (ROOT / rel).is_file():
            problems.append(f"deployment contract file is missing: {rel}")
    return problems


def problems_for_local_parity() -> list[str]:
    """Keep the local launcher aligned with the CI gates (R-O09)."""
    problems: list[str] = []
    required = (
        "check_k8s_manifest_contract.py",
        "check_migration_chain.py",
        "check_restore_drill.py",
        "check_ci_contracts.py",
    )
    for rel in ("scripts/test-all.sh", "scripts/test-all.ps1"):
        try:
            text = read(rel)
        except FileNotFoundError:
            problems.append(f"local launcher is missing: {rel}")
            continue
        for script in required:
            if script not in text:
                problems.append(f"{rel} does not run {script}")
        if "app/target/bom.json" not in text:
            problems.append(
                f"{rel} must inspect app/target/bom.json (the aggregate BOM "
                "CI generates), not target/bom.json"
            )
        if re.search(r"(?<!app/)target/bom\.json", text):
            problems.append(
                f"{rel} still references the obsolete root target/bom.json"
            )
    return problems


def problems_for_tool_only_sources() -> list[str]:
    problems: list[str] = []
    skip_dirs = {".git", "node_modules", "target", ".codegraph", "audit-work"}

    def walk(pattern: str):
        for path in ROOT.rglob(pattern):
            if skip_dirs.intersection(path.relative_to(ROOT).parts):
                continue
            yield path

    for pom in walk("pom.xml"):
        if "audit-work" in pom.read_text(encoding="utf-8"):
            problems.append(
                f"module pom references tool-only audit-work/: "
                f"{pom.relative_to(ROOT)}"
            )

    main_roots = (
        "account", "user", "transfer", "audit", "common", "web-support",
        "infrastructure", "persistence", "app", "account-api",
    )
    for module in main_roots:
        module_root = ROOT / module
        if not module_root.is_dir():
            continue
        for java in module_root.rglob("*.java"):
            if "target" in java.relative_to(ROOT).parts:
                continue
            text = java.read_text(encoding="utf-8")
            if "audit-work" in text or "audit.work" in text:
                problems.append(
                    f"main source references tool-only audit-work/: "
                    f"{java.relative_to(ROOT)}"
                )
    return problems


CHECKS = (
    ("mutable image tag", problems_for_mutable_image_tag),
    ("image-pin encoding", problems_for_image_pin_encoding),
    ("migration soak", problems_for_migration_soak),
    ("migration content", problems_for_migration_content),
    ("supply chain", problems_for_supply_chain),
    ("deployment contracts", problems_for_deployment_contracts),
    ("local parity", problems_for_local_parity),
    ("tool-only sources", problems_for_tool_only_sources),
)


def main() -> int:
    problems: list[str] = []
    for label, check in CHECKS:
        try:
            found = check()
        except (OSError, UnicodeError) as exc:
            found = [f"{label} check could not read its inputs: {exc}"]
        if found:
            problems.extend(f"{label}: {message}" for message in found)
            print(f"FAIL: {label}", file=sys.stderr)
            for message in found:
                print(f"  {message}", file=sys.stderr)

    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        return 1
    print(
        "CI contracts OK: image tag, image-pin encoding, migration "
        "soak/content, supply chain (incl. SHA-pinned actions), deployment "
        "presence, local parity, tool-only sources"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
