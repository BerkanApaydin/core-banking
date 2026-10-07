"""Read-only deployment smoke check. Uses only Python's standard library."""

import argparse
from dataclasses import asdict, dataclass
from http.client import HTTPException
import json
import math
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


MAX_BODY_BYTES = 65_536


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


@dataclass(frozen=True)
class CheckResult:
    path: str
    passed: bool
    detail: str
    elapsed_ms: int


def base_url(value):
    parsed = urlsplit(value)
    if (parsed.scheme not in {"http", "https"} or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.query or parsed.fragment):
        raise argparse.ArgumentTypeError(
            "Use an http(s) base URL without credentials, query or fragment")
    return value.rstrip("/")


def timeout_seconds(value):
    try:
        result = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("Timeout must be a finite positive number") from exc
    if not math.isfinite(result) or result <= 0:
        raise argparse.ArgumentTypeError("Timeout must be a finite positive number")
    return result


def check(opener, url, path, timeout, expected_http, expected_status):
    started = time.monotonic()
    passed = False
    request = Request(url + path, headers={"Accept": "application/json"}, method="GET")
    try:
        try:
            response = opener.open(request, timeout=timeout)
        except HTTPError as response_error:
            response = response_error
        with response:
            status = response.code
            content_type = response.headers.get_content_type()
            body = response.read(MAX_BODY_BYTES + 1)
        if status != expected_http:
            detail = f"HTTP {status}; expected {expected_http}"
        elif len(body) > MAX_BODY_BYTES:
            detail = "Response exceeded 64 KiB"
        elif content_type != "application/json" and not content_type.endswith("+json"):
            detail = "Expected a JSON response"
        else:
            payload = json.loads(body)
            passed = isinstance(payload, dict) and payload.get("status") == expected_status
            detail = "Contract satisfied" if passed else "Unexpected JSON status"
    except (OSError, URLError, HTTPException, ValueError) as exc:
        # Do not print bodies, tokens, connection strings or exception messages.
        detail = f"Request/response error ({type(exc).__name__})"
    return CheckResult(path, passed, detail, round((time.monotonic() - started) * 1000))


def check_prometheus(opener, url, timeout, allow_secured=False):
    """Regression for K1/D1: the scrape endpoint must be reachable.

    Prometheus exposition format is text/plain (not JSON), so the generic
    JSON contract checker cannot be reused. Pass = HTTP 200 with a body
    that looks like exposition format. A 401/403 here means the alarm
    pipeline is dead (whitelist regression); a 404 means actuator
    exposure is misconfigured.

    ``allow_secured`` exists because the two environments differ by design.
    Only ``application-prod.yml`` puts ``/actuator/prometheus`` in the
    whitelist; under dev/test it is intentionally secured. A single strict
    mode therefore cannot be used everywhere, and the tempting shortcut --
    not checking metrics at all outside prod -- loses the one signal dev *can*
    still see: that the endpoint exists at all. With ``allow_secured`` a
    401/403 passes ("exposed, gated by policy") while 404 still fails, so
    dropping ``prometheus`` from ``management.endpoints.web.exposure.include``
    is caught in CI instead of surfacing as a silently dead alarm line.
    """
    started = time.monotonic()
    request = Request(url + "/actuator/prometheus",
                      headers={"Accept": "text/plain"}, method="GET")
    try:
        try:
            response = opener.open(request, timeout=timeout)
        except HTTPError as response_error:
            response = response_error
        with response:
            status = response.code
            body = response.read(MAX_BODY_BYTES + 1)
        if status in (401, 403):
            if allow_secured:
                detail = (f"HTTP {status}; endpoint exposed and secured by policy "
                          f"(expected outside production)")
                passed = True
            else:
                detail = f"HTTP {status}; scrape endpoint secured, alarm pipeline dead (K1/D1)"
                passed = False
        elif status == 404:
            detail = "HTTP 404; actuator exposure misconfigured (K1/D1)"
            passed = False
        elif status != 200:
            detail = f"HTTP {status}; expected 200"
            passed = False
        elif len(body) > MAX_BODY_BYTES:
            detail = "Response exceeded 64 KiB"
            passed = False
        elif b"# HELP" in body or b"# TYPE" in body:
            detail = "Prometheus exposition format confirmed"
            passed = True
        else:
            detail = "Unexpected scrape body (not exposition format)"
            passed = False
    except (OSError, URLError, HTTPException, ValueError) as exc:
        detail = f"Request/response error ({type(exc).__name__})"
        passed = False
    return CheckResult("/actuator/prometheus", passed, detail,
                       round((time.monotonic() - started) * 1000))


def run_checks(url, timeout=5.0, require_probes=False, require_metrics=False,
               allow_metrics_secured=False):
    opener = build_opener(NoRedirects())
    contracts = [("/actuator/health", 200, "UP")]
    if require_probes:
        contracts.extend([
            ("/actuator/health/liveness", 200, "UP"),
            ("/actuator/health/readiness", 200, "UP"),
        ])
    # Browser session is protected but not covered by the IP rate limiter.
    # Checking /accounts here could yield 429 during legitimate load.
    contracts.append(("/api/v1/auth/browser/session", 401, 401))
    results = [check(opener, url, path, timeout, status, payload_status)
               for path, status, payload_status in contracts]
    if require_metrics:
        results.append(check_prometheus(opener, url, timeout, allow_metrics_secured))
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url", type=base_url, help="Application base URL; no bearer token needed")
    parser.add_argument("--timeout", type=timeout_seconds, default=5.0,
                        help="Socket timeout per request in seconds (default: 5)")
    parser.add_argument("--require-probes", action="store_true",
                        help="Also require liveness/readiness endpoints (e.g. Kubernetes)")
    parser.add_argument("--require-metrics", action="store_true",
                        help="Also require the Prometheus scrape endpoint (K1/D1 alarm-pipeline guard)")
    parser.add_argument("--allow-metrics-secured", action="store_true",
                        help="With --require-metrics, accept a deliberately secured scrape endpoint "
                             "(401/403) as long as it is exposed. Use where the profile does not "
                             "whitelist /actuator/prometheus, e.g. dev/test: this still catches the "
                             "endpoint disappearing entirely (404).")
    parser.add_argument("--json", action="store_true", help="Print machine-readable results")
    args = parser.parse_args(argv)
    if args.allow_metrics_secured and not args.require_metrics:
        parser.error("--allow-metrics-secured has no effect without --require-metrics")
    results = run_checks(args.base_url, args.timeout, args.require_probes, args.require_metrics,
                         args.allow_metrics_secured)
    if args.json:
        print(json.dumps({"passed": all(result.passed for result in results),
                          "checks": [asdict(result) for result in results]}))
    else:
        for result in results:
            print(f"{'PASS' if result.passed else 'FAIL'} {result.path}: "
                  f"{result.detail} ({result.elapsed_ms} ms)")
    return 0 if all(result.passed for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
