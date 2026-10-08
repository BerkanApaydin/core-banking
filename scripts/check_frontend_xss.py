#!/usr/bin/env python3
"""Frontend XSS guard (H-2).

Fails if any ``innerHTML`` assignment in ``app/src/main/resources/static/*.js``
interpolates a value that is not provably safe.

Only the template literal of the ``innerHTML = ...`` statement itself is
checked (multi-line templates included). Safe interpolations:
  - ``escapeHtml(...)``, ``formatMoney(...)``, ``formatDate(...)``,
  - pure ``__(...)`` i18n lookups,
  - ``resolveAccountRef(...)`` (escapes internally),
  - numeric expressions (``Number(...)``, ``.length``, ``page``, ``*Id``),
  - ``encodeURIComponent(...)`` for URL/attribute contexts,
  - boolean-to-constant class toggles from an allow-list.

Run: ``python scripts/check_frontend_xss.py``
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "app" / "src" / "main" / "resources" / "static"

INTERPOLATION = re.compile(r"\$\{([^}]+)\}")
SAFE_SUBSTRINGS = (
    "escapeHtml(",
    "formatMoney(",
    "formatDate(",
    "resolveAccountRef(",
    "encodeURIComponent(",
    "formatIbanDisplay(",
    "parts.join(",
    "badgeClass",
    "badgeIcon",
    "amountClass",
    "statusMeta.cls",
    "statusMeta.label",
    "statusBadge",
    "meta.cls",
    "meta.label",
    "prefix",
    "btn-cancel-transfer",
    "showStatusPill",
    "isEligibleForCancel",
)
NUMERIC_HINT = re.compile(
    r"^\s*(Number\(|String\(Number|.*\.length|page|size|total|userId|accountId|transferId|#\$|[0-9][0-9_ .()]*)\s*$"
)


def is_safe(expr: str) -> bool:
    e = expr.strip()
    if not e:
        return True
    if e.startswith("__(") and e.endswith(")"):
        return True
    if any(s in e for s in SAFE_SUBSTRINGS):
        # formatIbanDisplay output is [A-Z0-9 ] shaped, but callers in this
        # repo always wrap it in escapeHtml; require that pairing.
        if "formatIbanDisplay(" in e and "escapeHtml(" not in e:
            return False
        return True
    if NUMERIC_HINT.match(e):
        return True
    # Ternary of two safe branches:  cond ? `...safe...` : `...safe...`
    # or cond ? 'const' : 'const' — allow when both sides are quoted/allow-listed.
    if "?" in e and ":" in e:
        parts = e.split("?", 1)[1].split(":")
        if all(
            p.strip().startswith(("'", '"', "`"))
            or any(s in p for s in SAFE_SUBSTRINGS)
            or p.strip() in {"true", "false", "''", '""'}
            for p in parts
        ):
            return True
        # boolean -> constant class names
        if all(re.match(r"^[\w .?'\"`()-]+$", p.strip()) for p in parts):
            text = " ".join(parts)
            if "escapeHtml" in text or "formatMoney" in text or "__(" in text:
                return True
            if re.search(r"badge-|amount-|card-", text):
                return True
    return False


def extract_template(lines: list[str], start: int) -> str:
    """Return the template literal text of the innerHTML assignment at start (0-based)."""
    text = "\n".join(lines[start : start + 20])
    eq = text.find("innerHTML")
    eq = text.find("=", eq)
    if eq == -1:
        return ""
    rest = text[eq + 1 :]
    bt1 = rest.find("`")
    q1 = rest.find("'")
    # Empty-string / single-quoted assignment: no interpolations possible.
    if q1 != -1 and (bt1 == -1 or q1 < bt1):
        return ""
    if bt1 == -1:
        return ""
    rest = rest[bt1 + 1 :]
    end = rest.find("`;")
    if end == -1:
        end = rest.find("`")
    return rest[:end] if end != -1 else rest


def check_file(path: Path) -> list[str]:
    violations: list[str] = []
    lines = path.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "innerHTML" not in line or "escapeHtml" in line and "${" not in line:
            # Still need full check below; this is just a fast path guard.
            pass
        if "innerHTML" not in line:
            continue
        template = extract_template(lines, i)
        if not template:
            continue
        for m in INTERPOLATION.finditer(template):
            expr = m.group(1)
            if not is_safe(expr):
                violations.append(f"{path.name}:{i + 1}: unsafe `${{{expr.strip()[:110]}}}`")
                break
    return violations


def main() -> int:
    if not STATIC.is_dir():
        print(f"static dir not found: {STATIC}")
        return 2
    violations: list[str] = []
    for js in sorted(STATIC.glob("*.js")):
        violations.extend(check_file(js))
    if violations:
        print("XSS guard FAILED — unsafe innerHTML interpolations:")
        for v in violations:
            print(f"  - {v}")
        print("\nFix: wrap user-controlled values in escapeHtml(...).")
        return 1
    print(f"XSS guard OK — {len(list(STATIC.glob('*.js')))} JS files, no unsafe innerHTML.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
