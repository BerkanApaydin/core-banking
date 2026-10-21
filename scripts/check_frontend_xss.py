#!/usr/bin/env python3
"""Frontend XSS guard (H-2).

Fails if any HTML sink in ``app/src/main/resources/static/*.js`` interpolates a
value that is not provably safe. Checked sinks:

  - ``innerHTML = ...`` / ``innerHTML += ...``
  - ``outerHTML = ...`` / ``outerHTML += ...``
  - ``element.insertAdjacentHTML(position, html)``
  - ``document.write(...)`` / ``document.writeln(...)``

Only literal template/string arguments are considered provable. Safe
interpolations:
  - ``escapeHtml(...)``, ``formatMoney(...)``, ``formatDate(...)``,
  - ``resolveAccountRef(...)`` (escapes internally),
  - ``dictionaryHtml(...)`` (renders only bundled, developer-controlled keys),
  - numeric expressions (``Number(...)``, ``.length``, ``.total``, ``page``),
  - ``encodeURIComponent(...)`` for URL/attribute contexts,
  - boolean-to-constant class toggles from an allow-list.

``__(key, ...args)`` is only safe when the key is a static string literal and
every interpolated argument is itself provably safe. A dynamic key or a raw
argument is rejected: ``__('x', userControlled)`` renders attacker text.

Run: ``python scripts/check_frontend_xss.py``
Self-test: ``python scripts/check_frontend_xss.py --self-test``
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "app" / "src" / "main" / "resources" / "static"

ASSIGN_SINK = re.compile(r"\b(innerHTML|outerHTML)\s*\+?=")
ADJACENT_SINK = re.compile(r"\.insertAdjacentHTML\s*\(")
DOC_WRITE = re.compile(r"\bdocument\.(?:write|writeln)\s*\(")

# Function calls whose result is safe by contract. Whole-word matching keeps
# `unescapeHtml(` from passing as `escapeHtml(`.
SAFE_CALLS = re.compile(
    r"(?:\b(?:escapeHtml|formatMoney|formatDate|resolveAccountRef|"
    r"encodeURIComponent|formatIbanDisplay|dictionaryHtml)\s*\(|\bparts\.join\s*\()"
)
# Known constant/class-name variables assembled from static branches.
SAFE_EXACT = {
    "badgeClass",
    "badgeIcon",
    "amountClass",
    "statusMeta.cls",
    "statusMeta.label",
    "statusBadge",
    "meta.cls",
    "meta.label",
    "prefix",
    "showStatusPill",
    "isEligibleForCancel",
    "btn-cancel-transfer",
}
NUMERIC_HINT = re.compile(
    r"^\s*(?:Number\(|String\(Number|.*\.(?:length|total|page|size|count)|"
    r"page|size|count|total|userId|accountId|transferId|#\$|"
    r"[0-9][0-9_ .()+\-*/%]*)\s*$"
)


def _split_top_level(expr: str, sep: str = "+") -> list[str]:
    """Split on `sep` ignoring nesting (parens/brackets/braces) and quotes."""
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


def _template_content(text: str) -> str | None:
    """Return the body of a complete template literal, or None if malformed."""
    if not text.startswith("`"):
        return None
    i, n = 1, len(text)
    depth = 0
    quote: str | None = None
    escaped = False
    while i < n:
        ch = text[i]
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
                return text[1:i] if not text[i + 1 :].strip() else None
            i += 1
            continue
        if ch == "$" and i + 1 < n and text[i + 1] == "{":
            depth += 1
            i += 2
            continue
        if ch == "}" and depth > 0:
            depth -= 1
            i += 1
            continue
        i += 1
    return None


def _interpolations(template_body: str) -> list[str]:
    """Return `${...}` expressions, handling nested braces and sub-templates."""
    found: list[str] = []
    i, n = 0, len(template_body)
    while i < n:
        if not template_body.startswith("${", i):
            i += 1
            continue
        start = i + 2
        j = start
        depth = 1
        quote: str | None = None
        escaped = False
        while j < n:
            ch = template_body[j]
            if quote is not None:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == quote:
                    quote = None
            elif ch in "\"'`":
                quote = ch
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    found.append(template_body[start:j])
                    i = j + 1
                    break
            j += 1
        else:
            break
        if j >= n:
            break
    return found


def _split_ternary(expr: str) -> tuple[str, str, str] | None:
    question = _split_top_level(expr, "?")
    if len(question) != 2:
        return None
    tail = question[1]
    if tail.lstrip().startswith((".", "?")):
        return None
    branches = _split_top_level(tail, ":")
    if len(branches) < 2:
        return None
    return question[0], branches[0], ":".join(branches[1:])


def _is_i18n_lookup(expr: str) -> bool:
    """`__(staticKey, safeArg...)` is safe; dynamic keys/raw args are not."""
    if not (expr.startswith("__(") and expr.endswith(")")):
        return False
    args = _split_top_level(expr[3:-1], ",")
    if not args or not _is_quoted_literal(args[0]):
        return False
    return all(is_safe(arg) for arg in args[1:])


def is_safe(expr: str) -> bool:
    e = expr.strip()
    if not e:
        return True
    ternary = _split_ternary(e)
    if ternary is not None:
        return is_safe(ternary[1]) and is_safe(ternary[2])
    if e.startswith("`"):
        content = _template_content(e)
        if content is None:
            return False
        return all(is_safe(value) for value in _interpolations(content))
    if "+" in e:
        operands = _split_top_level(e)
        if len(operands) > 1:
            # Every concatenated piece must be safe (or a quoted literal) on
            # its own; a bare identifier next to escapeHtml(...) fails.
            return all(_is_quoted_literal(o) or is_safe(o) for o in operands)
    if _is_i18n_lookup(e):
        return True
    if SAFE_CALLS.search(e) or e in SAFE_EXACT:
        # formatIbanDisplay output is [A-Z0-9 ] shaped, but callers in this
        # repo always wrap it in escapeHtml; require that pairing.
        if re.search(r"\bformatIbanDisplay\s*\(", e) and not re.search(
                r"\bescapeHtml\s*\(", e):
            return False
        return True
    if NUMERIC_HINT.match(e):
        return True
    if _is_quoted_literal(e):
        return True
    return False


def _extract_statement(text: str, start: int) -> str:
    """Return the expression after a sink assignment/argument start."""
    i, n = start, len(text)
    depth = 0
    quote: str | None = None
    escaped = False
    while i < n:
        ch = text[i]
        if quote is not None:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            i += 1
            continue
        if ch in "\"'`":
            quote = ch
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth > 0:
                depth -= 1
        elif ch == ";" and depth == 0:
            break
        i += 1
    return text[start:i]


def _extract_call_args(text: str, open_paren: int) -> list[str]:
    """Split a call's arguments, respecting nested brackets and quotes."""
    args: list[str] = []
    current: list[str] = []
    i, n = open_paren + 1, len(text)
    depth = 0
    quote: str | None = None
    escaped = False
    while i < n:
        ch = text[i]
        if quote is not None:
            current.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
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
            if ch == ")" and depth == 0:
                args.append("".join(current))
                return args
            if depth > 0:
                depth -= 1
            current.append(ch)
        elif ch == "," and depth == 0:
            args.append("".join(current))
            current = []
        else:
            current.append(ch)
        i += 1
    return args


def check_expression(expr: str) -> list[str]:
    """Return the unsafe sub-expressions in an HTML argument."""
    e = expr.strip()
    if not e:
        return []
    ternary = _split_ternary(e)
    if ternary is not None:
        return check_expression(ternary[1]) + check_expression(ternary[2])
    if "+" in e:
        operands = _split_top_level(e)
        if len(operands) > 1:
            return [o.strip() for o in operands if not is_safe(o)]
    return [] if is_safe(e) else [e]


def check_source(name: str, text: str) -> list[str]:
    violations: list[str] = []

    for match in ASSIGN_SINK.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        expr = _extract_statement(text, match.end())
        for bad in check_expression(expr):
            violations.append(f"{name}:{line}: unsafe {match.group(1)} `${{{bad[:110]}}}`")

    for match in ADJACENT_SINK.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        args = _extract_call_args(text, match.end() - 1)
        if len(args) < 2 or not args[1].strip():
            violations.append(
                f"{name}:{line}: insertAdjacentHTML without a provable HTML argument")
            continue
        for bad in check_expression(args[1]):
            violations.append(f"{name}:{line}: unsafe insertAdjacentHTML `${{{bad[:110]}}}`")

    for match in DOC_WRITE.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        args = _extract_call_args(text, match.end() - 1)
        for arg in args:
            for bad in check_expression(arg):
                violations.append(f"{name}:{line}: unsafe document.write `${{{bad[:110]}}}`")

    return violations


def check_file(path: Path) -> list[str]:
    return check_source(path.name, path.read_text(encoding="utf-8"))


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
        ("__('transfer.cancel_btn', escapeHtml(x))", True),
        ("__('transfer.cancel_btn', userControlled)", False),
        ("__(key)", False),
        ("__('general.showing_of', accounts.length, accountPager.total)", True),
        ("isEligibleForCancel ? 'x' : ''", True),
        ("formatIbanDisplay(t.iban)", False),  # must pair with escapeHtml
        ("escapeHtml(formatIbanDisplay(t.iban))", True),
        ("`<p>${escapeHtml(x)}</p>`", True),
        ("`<p>${userControlled}</p>`", False),
        ("cond ? escapeHtml(a) : escapeHtml(b)", True),
        ("cond ? a : escapeHtml(b)", False),
    ]
    failures = [
        f"is_safe({expr!r}) = {is_safe(expr)}, want {want}"
        for expr, want in cases
        if is_safe(expr) is not want
    ]

    sink_cases = [
        ("el.innerHTML = `<p>${userControlled}</p>`;", False),
        ("el.innerHTML = `<p>${escapeHtml(userControlled)}</p>`;", True),
        ("el.outerHTML = `<p>${userControlled}</p>`;", False),
        ("el.outerHTML = `<p>${escapeHtml(userControlled)}</p>`;", True),
        ("el.insertAdjacentHTML('beforeend', `<p>${userControlled}</p>`);", False),
        ("el.insertAdjacentHTML('beforeend', `<p>${escapeHtml(userControlled)}</p>`);", True),
        ("el.insertAdjacentHTML('beforeend', payload);", False),
        ("el.innerHTML = __(key);", False),
        ("el.innerHTML = __('x', userControlled);", False),
        ("el.innerHTML = __('x', escapeHtml(userControlled));", True),
        ("el.innerHTML = '<p>' + escapeHtml(x) + '</p>';", True),
        ("el.innerHTML = '<p>' + x + '</p>';", False),
        ("el.innerHTML = dictionaryHtml(key);", True),
    ]
    for code, want_safe in sink_cases:
        got_safe = not check_source("vector.js", code)
        if got_safe is not want_safe:
            failures.append(f"check_source({code!r}) safe={got_safe}, want {want_safe}")

    # A 30-line template with the payload past the old 20-line window.
    long_code = "\n".join(["el.innerHTML = `"] + ["  <p>pad</p>"] * 24
                          + ["  ${userControlled}", "`;"])
    if not check_source("vector.js", long_code):
        failures.append("long template was not scanned to its closing backtick")

    if failures:
        print("XSS guard self-test FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"XSS guard self-test OK — {len(cases) + len(sink_cases) + 1} vectors.")
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
        print("XSS guard FAILED — unsafe HTML sink interpolations:")
        for v in violations:
            print(f"  - {v}")
        print("\nFix: wrap user-controlled values in escapeHtml(...) or use a safe sink.")
        return 1
    print(f"XSS guard OK — {len(list(STATIC.glob('*.js')))} JS files, no unsafe HTML sinks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
