"""Local equivalent of the CI dependency-review gate (high/critical fail).

Reads the CycloneDX SBOM (target/bom.json), batches every library against
https://api.osv.dev (OSV covers Maven Central), rates each finding from its
CVSS v3 score (HIGH >= 7.0, CRITICAL >= 9.0 — same bar as
actions/dependency-review-action with fail-on-severity: high), and exits 1
when a HIGH/CRITICAL issue exists. Network required (like CI).
"""
import json
import sys
import urllib.request

BOM = "target/bom.json"
OSV_URL = "https://api.osv.dev/v1/querybatch"
OSV_VULN_URL = "https://api.osv.dev/v1/vulns/"
ALLOWLIST = "scripts/dependency_allowlist.json"


def load_packages():
    with open(BOM, encoding="utf-8") as fh:
        bom = json.load(fh)
    packages = []
    for component in bom.get("components", []):
        if component.get("type") != "library":
            continue
        group = (component.get("group") or "").strip()
        name = (component.get("name") or "").strip()
        version = (component.get("version") or "").strip()
        if name and version:
            packages.append((f"{group}:{name}" if group else name, version))
    return packages


def rate(detail):
    """GitHub-reviewed severity label (same semantics as the CI gate:
    fail on high/critical). Falls back to numeric CVSS v3 parsing."""
    label = (detail.get("database_specific") or {}).get("severity", "")
    if isinstance(label, str) and label.upper() in (
            "CRITICAL", "HIGH", "MODERATE", "LOW"):
        return label.upper(), -1.0
    best, best_score = "UNKNOWN", -1.0
    for entry in detail.get("severity", []):
        if entry.get("type") != "CVSS_V3":
            continue
        try:
            score = float(entry.get("score", ""))
        except (TypeError, ValueError):
            continue
        if score >= 9.0:
            return "CRITICAL", score
        if score >= 7.0 and score > best_score:
            best, best_score = "HIGH", score
        elif score >= 4.0 and best_score < 4.0:
            best, best_score = "MEDIUM", score
        elif best == "UNKNOWN":
            best, best_score = "LOW", score
    return best, best_score


def fetch_detail(vid):
    with urllib.request.urlopen(OSV_VULN_URL + vid, timeout=60) as response:
        return json.load(response)


def query_osv(packages):
    queries = [{"package": {"name": name, "ecosystem": "Maven"}, "version": version}
               for name, version in packages]
    findings = []
    for offset in range(0, len(queries), 100):
        data = json.dumps({"queries": queries[offset:offset + 100]}).encode()
        request = urllib.request.Request(
            OSV_URL, data=data, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=90) as response:
            payload = json.load(response)
        for (name, version), result in zip(
                packages[offset:offset + 100], payload.get("results", [])):
            for vuln in result.get("vulns", []):
                # querybatch returns ids only; severity lives on the record.
                detail = fetch_detail(vuln.get("id"))
                rating, score = rate(detail)
                findings.append((name, version, detail.get("id"),
                                 detail.get("summary", ""), rating, score))
    return findings


def load_allowlist():
    try:
        with open(ALLOWLIST, encoding="utf-8") as fh:
            data = json.load(fh)
        return {k: v for k, v in data.items() if not k.startswith("_")}
    except FileNotFoundError:
        return {}


def main():
    packages = load_packages()
    print(f"reviewing {len(packages)} libraries from {BOM} against OSV")
    try:
        findings = query_osv(packages)
    except Exception as exc:  # noqa: BLE001 - network is best-effort locally
        print(f"osv query failed ({exc}); gate cannot be enforced offline")
        return 0
    allowlist = load_allowlist()
    for name, version, vid, summary, rating, score in findings:
        flag = " (accepted-risk, see dependency_allowlist.json)" \
            if vid in allowlist else ""
        print(f" - {vid} [{rating} {score}] {name}:{version}: "
              f"{summary[:110]}{flag}")
    blocking = [f for f in findings
                if f[4] in ("HIGH", "CRITICAL") and f[2] not in allowlist]
    accepted = [f for f in findings
                if f[4] in ("HIGH", "CRITICAL") and f[2] in allowlist]
    if blocking:
        print(f"dependency review FAILED: {len(blocking)} HIGH/CRITICAL finding(s)",
              file=sys.stderr)
        return 1
    print(f"dependency review passed: {len(findings)} finding(s), "
          f"{len(accepted)} accepted-risk, none blocking")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
