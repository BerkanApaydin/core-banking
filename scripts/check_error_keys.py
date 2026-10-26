"""Fail CI when an i18n problem-message key breaks the single-source contract.

Audit follow-up: production code must reference MessageKeys constants instead
of raw `"error.*"` literals (shotgun-surgery hotspot — a key rename previously
required touching every throw site and handler with no compiler help). This
script pins the contract at build time, complementing MessageKeysContractTest
(which runs inside the app module with the bundles on the classpath):

1. No raw `"error.*"` literal may appear in main sources, except the canonical
   definitions inside MessageKeys.java itself (test fixtures intentionally keep
   synthetic keys like `error.test` / `error.unknown_key` to exercise the
   unknown-key fallback path — test sources are out of scope).
2. Every `MessageKeys.<CONSTANT>` used in main sources must be defined in
   MessageKeys.java.
3. Every used key must resolve in both messages.properties and
   messages_tr.properties (a typo fails silently at runtime — MessageSource
   falls back to the default text).
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "app" / "src" / "main" / "resources" / "messages.properties"
BUNDLE_TR = ROOT / "app" / "src" / "main" / "resources" / "messages_tr.properties"
MESSAGE_KEYS = (
    ROOT / "common" / "src" / "main" / "java" / "com" / "bank" / "app"
    / "common" / "messages" / "MessageKeys.java"
)
MODULES = ["account", "user", "transfer", "audit", "common", "web-support", "infrastructure", "persistence", "app", "account-api"]


def bundle_keys(path: Path) -> set:
    keys = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        keys.add(line.split("=", 1)[0].strip())
    return keys


def message_key_constants() -> dict:
    consts = {}
    for name, key in re.findall(
        r'public\s+static\s+final\s+String\s+([A-Z0-9_]+)\s*=\s*"(error\.[a-z0-9_]+)"',
        MESSAGE_KEYS.read_text(encoding="utf-8"),
    ):
        consts[name] = key
    return consts


def main() -> int:
    if not BUNDLE.is_file():
        print(f"missing bundle: {BUNDLE}", file=sys.stderr)
        return 1
    if not MESSAGE_KEYS.is_file():
        print(f"missing MessageKeys: {MESSAGE_KEYS}", file=sys.stderr)
        return 1
    en = bundle_keys(BUNDLE)
    tr = bundle_keys(BUNDLE_TR) if BUNDLE_TR.is_file() else set()
    consts = message_key_constants()
    if not consts:
        print("no constants parsed from MessageKeys.java", file=sys.stderr)
        return 1

    raw_literals: dict[str, list[str]] = {}
    used_consts: dict[str, list[str]] = {}
    for module in MODULES:
        for java in (ROOT / module).rglob("*.java"):
            rel = str(java.relative_to(ROOT)).replace("\\", "/")
            if "/test/" in rel or "/target/" in rel:
                continue
            text = java.read_text(encoding="utf-8")
            if java.resolve() != MESSAGE_KEYS.resolve():
                for key in re.findall(r'"(error\.[a-z0-9_]+)"', text):
                    raw_literals.setdefault(key, []).append(rel)
            for const in re.findall(r"(?<!\{@code )MessageKeys\.([A-Z0-9_]+)", text):
                used_consts.setdefault(const, []).append(rel)

    failures = 0
    if raw_literals:
        print("raw error.* literals in main sources (use MessageKeys instead):", file=sys.stderr)
        for key in sorted(raw_literals):
            print(f"  {key} (used in {raw_literals[key][0]})", file=sys.stderr)
        failures += 1
    undefined = sorted(c for c in used_consts if c not in consts)
    if undefined:
        print("MessageKeys constants with no definition:", file=sys.stderr)
        for const in undefined:
            print(f"  {const} (used in {used_consts[const][0]})", file=sys.stderr)
        failures += 1
    used_keys = sorted({consts[c] for c in used_consts if c in consts})
    missing_en = [k for k in used_keys if k not in en]
    missing_tr = [k for k in used_keys if k not in tr]
    if missing_en:
        print("error keys missing from messages.properties:", file=sys.stderr)
        for key in missing_en:
            print(f"  {key}", file=sys.stderr)
        failures += 1
    if missing_tr:
        print("error keys missing from messages_tr.properties:", file=sys.stderr)
        for key in missing_tr:
            print(f"  {key}", file=sys.stderr)
        failures += 1
    if failures:
        return 1
    print(f"error keys ok: {len(consts)} constants, {len(used_keys)} used, all in both bundles.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
