"""Fail CI when the clean JaCoCo aggregate omits a module or falls below its gate."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


EXPECTED = {
    "app", "common", "persistence", "account-api", "account",
    "transfer", "user", "audit", "infrastructure",
}
MINIMUM = {"LINE": 0.80, "BRANCH": 0.70}


def ratio(group: ET.Element, kind: str) -> float | None:
    counter = next((c for c in group.findall("counter") if c.get("type") == kind), None)
    if counter is None:
        return None
    covered = int(counter.get("covered", "0"))
    missed = int(counter.get("missed", "0"))
    return covered / (covered + missed) if covered + missed else None


def main(path: Path) -> int:
    if not path.is_file():
        print(f"Missing aggregate coverage report: {path}", file=sys.stderr)
        return 1
    root = ET.parse(path).getroot()
    groups = {g.get("name"): g for g in root.findall("group")}
    missing = EXPECTED - groups.keys()
    if missing:
        print(f"Aggregate coverage omits modules: {', '.join(sorted(missing))}", file=sys.stderr)
        return 1

    failed = False
    for name in sorted(EXPECTED):
        for kind, minimum in MINIMUM.items():
            actual = ratio(groups[name], kind)
            if actual is None:
                if kind == "BRANCH" and not groups[name].findall(".//counter[@type='BRANCH']"):
                    print(f"{name} BRANCH: N/A (no instrumentable branches)")
                else:
                    print(f"{name}: no {kind} counter; coverage cannot be enforced", file=sys.stderr)
                    failed = True
            else:
                print(f"{name} {kind}: {actual:.1%} (minimum {minimum:.0%})")
                failed |= actual < minimum
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(Path(sys.argv[1])))
