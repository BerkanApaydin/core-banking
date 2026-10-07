#!/usr/bin/env python3
"""Fail if any Spring field injection remains in test sources.

Constructor injection is the project standard (main code has zero field
injection, enforced by architecture tests). Test code follows the same rule;
the two sanctioned alternatives are test-class constructors and parameters on
lifecycle/callback methods (e.g. @BeforeEach).

Only @Autowired/@Value on FIELDS are violations:
- @Autowired/@Value on constructors or methods is fine.
- @MockitoBean/@Mock/@Container/@RegisterExtension/@TempDir etc. are not
  Spring injection and are ignored.
- @Bean methods in @TestConfiguration classes are definitions, not injection.

Usage: python3 scripts/check_test_injection.py [repo_root]
Exit 0 when clean, 1 with the offending locations otherwise.
"""

import re
import sys
from pathlib import Path

FIELD_RE = re.compile(
    r"^\s*private\s+(?!static\b)(?!final\b[\w<>\[\]?,\s]*\([^;]*\))"
    r"[\w<>\[\]?,\s]+\s+\w+\s*;",
)

SKIP_ANNOTATIONS = ("@MockitoBean", "@Mock,", "@Mock(", "@Mock ", "@Container")


def violations_in(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    hits: list[str] = []
    i = 0
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped in ("@Autowired", "@Value") or stripped.startswith(
            ("@Autowired(", "@Value(")
        ):
            # Look ahead past blank lines/comments to the member declaration.
            j = i + 1
            while j < len(lines) and (
                not lines[j].strip() or lines[j].strip().startswith(("//", "*", "/*"))
            ):
                j += 1
            if j >= len(lines):
                break
            decl = lines[j].strip()
            if decl.startswith(SKIP_ANNOTATIONS):
                i = j
                continue
            # A declaration containing '(' before ';' is a constructor/method.
            head = decl.split(";")[0]
            if "(" not in head and FIELD_RE.match(lines[j]):
                hits.append(f"{path}:{j + 1}: {decl}")
            i = j
        i += 1
    return hits


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
    offenders: list[str] = []
    for path in sorted(root.rglob("*.java")):
        if "src/test" not in path.as_posix():
            continue
        offenders.extend(violations_in(path))
    if offenders:
        print("Spring field injection in tests (use constructors or method parameters):")
        for hit in offenders:
            print(f"  {hit}")
        return 1
    print("No Spring field injection in tests.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
