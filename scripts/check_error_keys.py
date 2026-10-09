"""Fail CI when a Java `error.<key>` literal has no messages bundle entry.

32 hand-written keys live in messages.properties/_tr; a typo fails silently
at runtime (MessageSource falls back to the default text). This pins the
contract at build time instead.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "app" / "src" / "main" / "resources" / "messages.properties"
BUNDLE_TR = ROOT / "app" / "src" / "main" / "resources" / "messages_tr.properties"
MODULES = ["account", "user", "transfer", "audit", "common", "infrastructure", "persistence", "app", "account-api"]


def bundle_keys(path: Path) -> set:
    keys = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        keys.add(line.split("=", 1)[0].strip())
    return keys


def main() -> int:
    if not BUNDLE.is_file():
        print(f"missing bundle: {BUNDLE}", file=sys.stderr)
        return 1
    en = bundle_keys(BUNDLE)
    tr = bundle_keys(BUNDLE_TR) if BUNDLE_TR.is_file() else set()
    used: dict[str, list[str]] = {}
    for module in MODULES:
        for java in (ROOT / module).rglob("*.java"):
            if "/test/" in str(java).replace("\\", "/") or "/target/" in str(java):
                continue
            text = java.read_text(encoding="utf-8")
            for key in re.findall(r'"(error\.[a-z0-9_]+)"', text):
                used.setdefault(key, []).append(str(java.relative_to(ROOT)))
    missing_en = sorted(k for k in used if k not in en)
    missing_tr = sorted(k for k in used if k not in tr)
    if missing_en:
        print("error keys missing from messages.properties:", file=sys.stderr)
        for key in missing_en:
            print(f"  {key} (used in {used[key][0]})", file=sys.stderr)
        return 1
    if missing_tr:
        print("error keys missing from messages_tr.properties:", file=sys.stderr)
        for key in missing_tr:
            print(f"  {key} (used in {used[key][0]})", file=sys.stderr)
        return 1
    print(f"error keys ok: {len(used)} keys in both bundles.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
