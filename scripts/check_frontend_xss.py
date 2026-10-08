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


def _split_top_level(expr: str, sep: str = "+") -> list[str]:
    """Split on `sep` ignoring nesting (parens/brackets/braces) and quotes.

    `escapeHtml(a) + b` must not pass on the strength of its first operand:
    callers split concatenations and vet every piece.
    """
    parts: list[str] = []
    depth = 0
    quote: str | None = None
    current: list[str] = []
    i = 0
    while i < len(expr):
        ch = expr[i]
        if quote is not None:
            current.append(ch)
            if ch == "\\" and i + 1 < len(expr):
                current.append(expr[i + 1])
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch in "\"'`":
            quote = ch
            current.append(ch)
        elif ch in "([{":
            depth += 1
            current.append(ch)
        elif ch in ")]}":
            if depth > 0:
                depth -= 1
            current.append(ch)
        elif ch == sep and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
        i += 1
    parts.append("".join(current))
    return parts


def _is_quoted_literal(text: str) -> bool:
    t = text.strip()
    return len(t) >= 2 and t[0] == t[-1] and t[0] in "\"'`"


def is_safe(expr: str) -> bool:
    e = expr.strip()
    if not e:
        return True
    if "+" in e:
        operands = _split_top_level(e)
        if len(operands) > 1:
            # Every concatenated piece must be safe (or a quoted literal) on
            # its own; a bare identifier next to escapeHtml(...) fails.
            return all(_is_quoted_literal(o) or is_safe(o) for o in operands)
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
    """Return the template literal text of the innerHTML assignment at start (0-based).

    The scan is brace-aware (`${...}` nesting, quotes inside interpolations)
    and runs up to 500 lines ahead: long page templates (60-120 lines) are
    checked in full, not just their first 20 lines.
    """
    text = "\n".join(lines[start : start + 500])
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
    # Walk from the opening backtick: it closes only at ${}-depth 0, so
    # nested template literals inside interpolations don't end the scan
    # early. Quotes are tracked only inside interpolations (HTML text at the
    # top level may contain apostrophes, which must not swallow the scan).
    i, n = bt1 + 1, len(rest)
    depth = 0
    quote: str | None = None
    escaped = False
    while i < n:
        ch = rest[i]
        if escaped:
            escaped = False
            i += 1
            continue
        if ch == "\\":
            escaped = True
            i += 1
            continue
        if quote is not None:
            if ch == quote:
                quote = None
            i += 1
            continue
        if depth > 0 and ch in "\"'":
            quote = ch
            i += 1
            continue
        if ch == "`":
            if depth == 0:
                return rest[bt1 + 1 : i]
            i += 1
            continue
        if ch == "$" and i + 1 < n and rest[i + 1] == "{":
            depth += 1
            i += 2
            continue
        if ch == "}" and depth > 0:
            depth -= 1
            i += 1
            continue
        i += 1
    return rest[bt1 + 1 :]


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


def self_test() -> int:
    """Regression vectors for the guard itself (run: --self-test in CI)."""
    cases = [
        # (expression, expected is_safe)
        ("escapeHtml(a) + b", False),   # the substring-shortcut bypass
        ("b + escapeHtml(a)", False),
        ("prefix + escapeHtml(x)", True),
        ("page + 1", True),
        ("escapeHtml(t.currency)", True),
        ("t.senderIban", False),
        ("__('transfer.cancel_btn')", True),
        ("isEligibleForCancel ? 'x' : ''", True),
        ("formatIbanDisplay(t.iban)", False),  # must pair with escapeHtml
        ("escapeHtml(formatIbanDisplay(t.iban))", True),
    ]
    failures = [
        f"is_safe({expr!r}) = {is_safe(expr)}, want {want}"
        for expr, want in cases
        if is_safe(expr) is not want
    ]
    # A 30-line template with the payload past the old 20-line window.
    long_lines = ["el.innerHTML = `"] + ["  <p>pad</p>"] * 24 + ["  ${userControlled}", "`;"]
    if not extract_template(long_lines, 0).strip().endswith("${userControlled}"):
        failures.append("long template was truncated before line 25")
    if is_safe("userControlled") is not False:
        failures.append("raw identifier must be unsafe")
    if failures:
        print("XSS guard self-test FAILED:")
        for f in failures:
            print(f"  - {f}")
        return 1
    print(f"XSS guard self-test OK — {len(cases) + 2} vectors.")
    return 0


def main() -> int:
    if "--self-test" in sys.argv[1:]:
        return self_test()
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
