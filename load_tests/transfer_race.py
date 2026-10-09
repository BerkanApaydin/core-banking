"""Transfer-race load probe (I-02).

Hammers the SAME account pair with concurrent POST /transfers to exercise
the pessimistic-lock path (SELECT ... FOR UPDATE in stable ID order via
OrderedPair) and the 30s use-case transaction bound.

Acceptance (prints PASS/FAIL):
  - zero HTTP 5xx (a 500 under lock contention is the failure mode this
    probe exists to catch — lock pile-ups must surface as 409/429, never 500);
  - money conservation: (A1 + A2) balance before == after;
  - p95 latency reported for capacity modelling (no hard gate — record it
    in docs/slo.md after a staging run).

Requires a target with funded accounts (simulation profile or dev with
simulation funds enabled — see /accounts/capabilities). Run against an
isolated target with raised rate limits; stock budgets will 429 the race.

Usage:
  python3 load_tests/transfer_race.py [--threads 32] [--transfers 200] [--amount 10.00]
  TARGET_URL=http://localhost:8080 python3 load_tests/transfer_race.py
"""

import argparse
import math
import os
import statistics
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed

try:
    import requests
except ModuleNotFoundError:  # pragma: no cover - dependency bootstrap
    requests = None


def _require_requests():
    if requests is None:
        print("requests is required: pip install -r load_tests/requirements.txt")
        sys.exit(2)

BASE_URL = os.environ.get("TARGET_URL", "http://localhost:8080").rstrip("/")
CURRENCY = "TRY"


def _post(path, *, token=None, payload=None, idem=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if idem:
        headers["Idempotency-Key"] = idem
    return requests.post(f"{BASE_URL}{path}", json=payload, headers=headers,
                         timeout=(3, 30))


def _get(path, token):
    return requests.get(f"{BASE_URL}{path}",
                        headers={"Authorization": f"Bearer {token}"},
                        timeout=(3, 30))


def main(argv=None):
    args = _parse(argv)
    _require_requests()
    user = f"race-{uuid.uuid4().hex[:8]}"
    password = "TestPass123!"

    r = _post("/api/v1/auth/register",
              payload={"username": user, "password": password})
    assert r.status_code in (200, 201), f"register failed: {r.status_code} {r.text}"
    r = _post("/api/v1/auth/login",
              payload={"username": user, "password": password})
    assert r.status_code == 200, f"login failed: {r.status_code} {r.text}"
    token = r.json()["token"]

    cap = _get("/api/v1/accounts/capabilities", token).json()
    if not cap.get("initialFundingEnabled", False):
        print("SKIP: target has simulation funding disabled — "
              "run with the simulation profile for funded accounts.")
        return 2

    seed = args.transfers * args.amount * 2 + 1000.0
    a1_id, a1_iban = _create(token, "Race A1", seed)
    a2_id, a2_iban = _create(token, "Race A2", seed)
    before = _total(token)
    print(f"racing {args.transfers} x {args.amount:.2f} {CURRENCY} "
          f"A1({a1_id}) -> A2({a2_id}) on {args.threads} threads; total before={before:.2f}")

    lat, statuses = [], {}
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.threads) as pool:
        futs = [pool.submit(_fire, token, a1_iban, a2_iban, args.amount)
                for _ in range(args.transfers)]
        for f in as_completed(futs):
            code, elapsed = f.result()
            statuses[code] = statuses.get(code, 0) + 1
            lat.append(elapsed)
    wall = time.perf_counter() - start

    after = _total(token)
    lat.sort()
    p50 = lat[len(lat) // 2]
    p95 = lat[min(len(lat) - 1, math.ceil(len(lat) * 0.95) - 1)]
    ok_201 = statuses.get(201, 0)
    err_5xx = sum(n for c, n in statuses.items() if 500 <= c < 600)
    print(f"statuses={statuses} wall={wall:.1f}s rps={len(lat)/wall:.1f} "
          f"p50={p50*1000:.0f}ms p95={p95*1000:.0f}ms")
    print(f"total after={after:.2f} (drift={after - before:.2f})")

    failures = []
    if err_5xx:
        failures.append(f"{err_5xx} HTTP 5xx under contention (must be 0)")
    if abs(after - before) > 0.01:
        failures.append(f"money not conserved: drift={after - before:.2f}")
    if ok_201 != args.transfers:
        print(f"NOTE: {args.transfers - ok_201} non-201 outcomes "
              f"(409 optimistic-conflict / 429 rate-limit are expected under "
              f"stock budgets — raise app.security.rate-limit for capacity runs).")
    if failures:
        print("FAIL: " + "; ".join(failures))
        return 1
    print(f"PASS: {ok_201}/{args.transfers} x 201, no 5xx, money conserved")
    return 0


def _parse(argv):
    p = argparse.ArgumentParser(description="Concurrent same-pair transfer race")
    p.add_argument("--threads", type=int, default=32)
    p.add_argument("--transfers", type=int, default=200)
    p.add_argument("--amount", type=float, default=10.00)
    return p.parse_args(argv)


def _create(token, owner, balance):
    r = _post("/api/v1/accounts", token=token, idem=str(uuid.uuid4()),
              payload={"ownerName": owner, "initialBalance": balance,
                       "currency": CURRENCY})
    assert r.status_code == 201, f"account create failed: {r.status_code} {r.text}"
    body = r.json()
    return body["id"], body["iban"]


def _total(token):
    return sum(a["balance"] for a in _get("/api/v1/accounts", token).json()["content"])


def _fire(token, sender_iban, receiver_iban, amount):
    t0 = time.perf_counter()
    try:
        r = _post("/api/v1/transfers", token=token, idem=str(uuid.uuid4()),
                  payload={"senderIban": sender_iban, "receiverIban": receiver_iban,
                           "amount": amount, "currency": CURRENCY})
        return r.status_code, time.perf_counter() - t0
    except Exception:
        return -1, time.perf_counter() - t0


if __name__ == "__main__":
    sys.exit(main())
