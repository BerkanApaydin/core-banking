#!/usr/bin/env python3
"""Dependency-free static-analysis gate (audit follow-up).

SpotBugs (effort=Max, threshold=Medium) runs in Maven verify and owns
bytecode-level bug patterns. This script complements it with structural and
style rules that SpotBugs does not check. Checkstyle/PMD remain unwired: their
engine artifacts are not in the offline build cache; see
docs/decisions/static-analysis.md.

1. Long methods: no main-source method body longer than MAX_METHOD_LINES.
   (Motivation: JwtAuthenticationFilter.doFilterInternal grew to ~133 lines
   with five interleaved decisions before it was split into named predicates;
   LoginUserUseCaseImpl/RefreshSessionUseCaseImpl got the same treatment.)
   The scanner masks comments, strings, chars and text blocks before brace
   counting, and detects signatures whose ``{`` is on a later line.
2. Config-knob literals: BCrypt cost, HSTS max-age and cache bounds must come
   from @ConfigurationProperties records, never inline literals in the
   adapters that consume them.
3. No System.out/err or printStackTrace in main sources (logging only).
4. No wildcard imports, ambient randomness/time (Math.random, new Random,
   System.currentTimeMillis, new Date) or unbounded cached thread pools in
   main sources. Inject Clock/SecureRandom (or use the documented policy)
   instead so behavior stays testable and reproducible.

Run: ``python scripts/check_code_health.py``
Self-test: ``python scripts/check_code_health.py --self-test``
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["account", "user", "transfer", "audit", "common", "web-support",
           "infrastructure", "persistence", "app", "account-api"]

# Bodies above this fail the gate. Measured 2026-10: the longest production
# methods are validateProductionConfig (~88, a sequential require* checklist),
# the refresh-rotation helpers (~50) and the outbox wiring (~56).
MAX_METHOD_LINES = 90

# (file suffix, forbidden regex, why) - literals that must live in
# @ConfigurationProperties records instead.
LITERAL_RULES = [
    ("SecurityConfig.java", r"new\s+BCryptPasswordEncoder\s*\(\s*\d+",
     "BCrypt cost must come from SecurityProperties.passwordEncoderStrength"),
    ("SecurityConfig.java", r"maxAgeInSeconds\s*\(\s*\d+",
     "HSTS max-age must come from SecurityProperties.hstsMaxAgeSeconds"),
    ("CaffeineRateLimiter.java", r"maximumSize\s*\(\s*\d+",
     "cache bound must come from RateLimitProperties.cacheMaxSize"),
    ("CaffeineLoginAttemptAdapter.java", r"maximumSize\s*\(\s*\d+",
     "cache bound must come from LoginAttemptProperties.cacheMaxSize"),
]

PRINT_RE = re.compile(r"System\.(out|err)\.print|\.printStackTrace\s*\(")

# Applied to every main-source file. Keep this list short and justified:
# each rule must be clean on the current tree and catch a real class of bug.
GLOBAL_RULES = [
    (re.compile(r"^\s*import\s+[\w.]+\.\*\s*;", re.MULTILINE),
     "wildcard import - list explicit imports so dependencies are reviewable"),
    (re.compile(r"\bMath\.random\s*\("),
     "Math.random is ambient and untestable - inject SecureRandom or use ThreadLocalRandom with a policy"),
    (re.compile(r"\bnew\s+(?:java\.util\.)?Random\s*\("),
     "unseeded Random - use SecureRandom for tokens or ThreadLocalRandom for jitter"),
    (re.compile(r"\bSystem\.currentTimeMillis\s*\("),
     "wall-clock call bypasses the injected Clock - use Clock/Instant"),
    (re.compile(r"\bnew\s+(?:java\.util\.)?Date\s*\("),
     "legacy Date bypasses java.time + Clock - use Instant with the injected Clock"),
    (re.compile(r"\bExecutors\.newCachedThreadPool\b"),
     "unbounded cached thread pool - size executors explicitly or use a virtual-thread executor"),
]
TYPE_RE = re.compile(r"\b(?:class|interface|enum|record)\s+[A-Za-z_$]")
CONTROL_RE = re.compile(
    r"^(?:if|for|while|switch|catch|synchronized|try|do|return|throw|new|"
    r"else|case|assert|yield)\b")
METHOD_ANNOTATION_RE = re.compile(r"^\s*@[\w.]+(?:\s*\([^)]*\))?")


def main_files():
    for module in MODULES:
        for java in (ROOT / module).rglob("*.java"):
            rel = str(java.relative_to(ROOT)).replace("\\", "/")
            if "/test/" in rel or "/target/" in rel:
                continue
            yield java


def mask_java(text):
    """Blank comments/strings/chars/text blocks, preserving length + newlines."""
    out = list(text)
    i, n = 0, len(text)

    def blank(start, end):
        for j in range(start, end):
            if out[j] != "\n":
                out[j] = " "

    while i < n:
        if text.startswith("//", i):
            end = text.find("\n", i)
            end = n if end < 0 else end
            blank(i, end)
            i = end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            blank(i, end)
            i = end
        elif text.startswith('"""', i):
            j = i + 3
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text.startswith('"""', j):
                    j += 3
                    break
                j += 1
            blank(i, min(j, n))
            i = min(j, n)
        elif text[i] in ('"', "'"):
            quote = text[i]
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == quote or text[j] == "\n":
                    j += 1
                    break
                j += 1
            blank(i, min(j, n))
            i = min(j, n)
        else:
            i += 1
    return "".join(out)


def _looks_like_method(signature):
    sig = " ".join(signature.split())
    if not sig or "(" not in sig or ")" not in sig:
        return False
    if "->" in sig or "=>" in sig or ";" in sig:
        return False
    if TYPE_RE.search(sig) or CONTROL_RE.match(sig):
        return False
    # Assignment (but not ==, !=, <=, >=) rules out field initializers.
    if re.search(r"(^|[^=!<>])=(?!=)", sig):
        return False
    return True


def _strip_annotations(line):
    """Return the declaration remainder for a line that may start with @Ann."""
    rest = line
    while True:
        match = METHOD_ANNOTATION_RE.match(rest)
        if not match or not rest.lstrip().startswith("@"):
            return rest
        rest = rest[match.end():]
        if not rest.strip():
            return ""


def _type_body_brace(line, paren_depth):
    """Find the type-body `{` while ignoring braces inside annotation args."""
    for ch in line:
        if ch == "(":
            paren_depth += 1
        elif ch == ")":
            paren_depth = max(0, paren_depth - 1)
        elif ch == "{" and paren_depth == 0:
            return True, paren_depth
    return False, paren_depth


def method_lengths(text):
    """Yield (signature, body_lines), masking comments/strings and multi-line sigs."""
    masked = mask_java(text)
    lines = masked.splitlines()
    raw_lines = text.splitlines()
    depth = 0
    type_depths = set()
    type_pending = None
    type_paren = 0
    annotation_depth = 0
    method_start = None
    method_base = None
    method_sig = ""
    pending = []

    for i, line in enumerate(lines):
        stripped = line.strip()

        if method_start is not None:
            depth += line.count("{") - line.count("}")
            if depth <= method_base:
                yield method_sig, i - method_start + 1
                method_start = None
                method_base = None
                method_sig = ""
            continue

        if type_pending is not None:
            body_brace, type_paren = _type_body_brace(line, type_paren)
            if body_brace:
                type_depths.add(type_pending + 1)
                type_pending = None
            elif ";" in line:
                type_pending = None
            depth += line.count("{") - line.count("}")
            continue

        if annotation_depth > 0:
            annotation_depth += line.count("(") - line.count(")")
            if annotation_depth < 0:
                annotation_depth = 0
            continue

        if stripped.startswith("@"):
            pending = []
            remainder = _strip_annotations(line).strip()
            if not remainder:
                continue
            if remainder.startswith("@"):
                annotation_depth = max(0, line.count("(") - line.count(")"))
                continue
            if remainder.startswith("("):
                annotation_depth = max(1, line.count("(") - line.count(")"))
                continue
            stripped = remainder
            line = remainder

        if not stripped:
            pending = []
            continue

        if TYPE_RE.search(line):
            pending = []
            body_brace, type_paren = _type_body_brace(line, 0)
            if body_brace:
                type_depths.add(depth + 1)
                type_pending = None
            else:
                type_pending = depth
            depth += line.count("{") - line.count("}")
            continue

        if depth in type_depths:
            pending.append((i, stripped))
            signature = " ".join(part for _, part in pending)
            opens = line.count("{")
            if ";" in line and not opens:
                pending = []
            elif opens:
                if _looks_like_method(signature):
                    method_start = pending[0][0]
                    method_base = depth
                    method_sig = " ".join(raw_lines[k].strip()
                                          for k, _ in pending)[:120]
                elif re.search(r"\bnew\b", signature) and not line.count("}"):
                    type_depths.add(depth + 1)
                pending = []

        depth += line.count("{") - line.count("}")
        if depth < 0:
            depth = 0
        if method_start is not None and depth <= method_base:
            yield method_sig, i - method_start + 1
            method_start = None
            method_base = None
            method_sig = ""
        type_depths = {d for d in type_depths if d <= depth}

    if method_start is not None:
        yield method_sig, len(lines) - method_start


def self_test():
    """Regression vectors for the parser itself (run: --self-test in CI)."""
    failures = []

    def methods(source):
        return list(method_lengths(source))

    sample = """class A {
    void one() {
        if (true) {
            run();
        }
    }
    public void multi(
        String a,
        String b) {
        run();
    }
}
"""
    found = methods(sample)
    if len(found) != 2:
        failures.append(f"simple/multi-line scan found {len(found)} methods, want 2: {found}")
    if found and any("multi" in sig for sig, _ in found):
        length = dict((sig, n) for sig, n in found)
        sig = next((s for s in length if "multi" in s), "")
        if length.get(sig) != 5:
            failures.append(f"multi-line method length {length.get(sig)}, want 5")

    braces = """class B {
    void tricky() {
        String s = "}";
        String t = \"\"\"
            }
            \"\"\";
        // }
        /* } */
        run();
    }
}
"""
    found = methods(braces)
    if len(found) != 1 or found[0][1] != 9:
        failures.append(f"masked braces scan {found}, want one 9-line method")

    lambda_field = """class C {
    private final Runnable r = () -> {
        run();
    };
    void real() {
        run();
    }
}
"""
    found = methods(lambda_field)
    if len(found) != 1 or "real" not in found[0][0]:
        failures.append(f"lambda field counted as method: {found}")

    annotated = """class D {
    @Override
    @SuppressWarnings({"x", "y"})
    public void annotated() {
        run();
    }
}
"""
    found = methods(annotated)
    if len(found) != 1 or found[0][1] != 3:
        failures.append(f"annotation scan {found}, want one 3-line method")

    interface_default = """interface I {
    default int x() {
        return 1;
    }
}
"""
    found = methods(interface_default)
    if len(found) != 1 or found[0][1] != 3:
        failures.append(f"interface default scan {found}, want one 3-line method")

    inline_annotation = """class G {
    @Deprecated public void annotated(
        String value) {
        run();
    }
}
"""
    found = methods(inline_annotation)
    if len(found) != 1 or found[0][1] != 4:
        failures.append(f"inline annotation scan {found}, want one 4-line method")

    record_header = """record R(
    @Ann
    @SuppressWarnings({"x", "y"}) String x
) {
    public R(String x) {
        this.x = x;
    }
}
"""
    found = methods(record_header)
    if len(found) != 1 or found[0][1] != 3:
        failures.append(f"record-header scan {found}, want one 3-line constructor")

    long_body = "class E {\n    void huge() {\n" + "        run();\n" * 95 + "    }\n}\n"
    found = methods(long_body)
    if len(found) != 1 or found[0][1] <= MAX_METHOD_LINES:
        failures.append(f"long method not detected: {found}")

    if failures:
        print("code-health self-test FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("code-health self-test OK - 8 vectors.")
    return 0


def main() -> int:
    if "--self-test" in sys.argv[1:]:
        return self_test()
    failures = []
    for java in main_files():
        rel = str(java.relative_to(ROOT)).replace("\\", "/")
        try:
            text = java.read_text(encoding="utf-8")
        except OSError as e:
            print(f"cannot read {rel}: {e}", file=sys.stderr)
            return 1
        for sig, length in method_lengths(text):
            if length > MAX_METHOD_LINES:
                failures.append(f"{rel}: method over {MAX_METHOD_LINES} lines ({length}): {sig}")
        for suffix, pattern, why in LITERAL_RULES:
            if java.name == suffix and re.search(pattern, text):
                failures.append(f"{rel}: {why}")
        if PRINT_RE.search(text):
            failures.append(f"{rel}: System.out/err or printStackTrace in main sources (use logging)")
        for pattern, why in GLOBAL_RULES:
            if pattern.search(text):
                failures.append(f"{rel}: {why}")
    if failures:
        print("static-analysis gate failures:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1
    print(f"static analysis ok (methods <= {MAX_METHOD_LINES} lines, no config literals, "
          f"no stdout, no ambient randomness/time, no wildcard imports).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
