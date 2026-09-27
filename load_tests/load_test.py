import os
import time
import uuid
import random
import datetime
import json
import math
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests

# ==============================================================================
#                             TEST CONFIGURATIONS
# ==============================================================================
BASE_URL = os.environ.get("TARGET_URL", "http://localhost:8080")
# Bootstrap opens funded accounts; target a dev/demo deployment, where demo
# funding is explicitly enabled. Production account opening starts at zero.

# --- Multiple Test Users ---
# In real-world scenarios, testing with a single token is unrealistic with thousands of users.
# During bootstrap, users are created and each gets an account.
NUM_LOAD_USERS     = 50
BOOTSTRAP_CONCURRENCY = 10

# --- Account Information for Transfer Tests ---
SENDER_IBAN   = None
RECEIVER_IBAN = None
TRANSFER_AMOUNT_RANGE = (0.01, 500.0)
TRANSFER_CURRENCY = "TRY"

# --- Think Time ---
# Real users pause to read the page and fill forms.
# Load test: 0.2-1.0s (realistic), Capacity test: 0 for max throughput.
THINK_TIME_RANGE = (0.2, 1.0)

# --- Warm-up ---
# Runs before load / capacity tests to warm JIT, DB pool, caches.
WARMUP_REQUESTS = 500
WARMUP_CONCURRENCY = 10

# --- Static Load Test Configuration (Test 3) ---
# Each phase runs for at least MIN_PHASE_DURATION seconds (sustained load).
# Ramp-up: gradual concurrency increase with realistic think time.
LOAD_TEST_CONCURRENCY    = 50
MIN_PHASE_DURATION       = 15.0
RAMP_UP_PHASES = [
    (10, "warm"),    # Warm-up: low load
    (25, "medium"),  # Medium load
    (50, "high"),    # High load
    (75, "peak"),    # Peak
]

# --- Capacity Finder Configuration (Test 4) ---
# No think time (pure throughput test), dynamic concurrency steps.
CAPACITY_TEST_TARGET_LATENCY_MS    = 200.0
CAPACITY_TEST_CONCURRENCY_STEPS    = [5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 75, 100, 125, 150, 200, 250, 300]

# --- Workload Ratios (must sum to 1.0) ---
R_ACCOUNTS_LIST    = 0.20
R_ACCOUNT_BY_ID    = 0.10
R_ACCOUNT_BY_IBAN  = 0.10
R_TRANSFER_DETAIL  = 0.15
R_TRANSFER_HISTORY = 0.20
R_TRANSFER_REPORT  = 0.15
W_REGISTER         = 0.01
W_TRANSFER         = 0.07
W_CREATE_ACCOUNT   = 0.01
W_CANCEL_TRANSFER  = 0.01

AUTH_IP            = f"192.168.1.{random.randint(10, 99)}"
RATE_LIMIT_TEST_IP = f"192.168.2.{random.randint(10, 99)}"

# Per-request source IP pool
IP_POOL = [f"10.0.{random.randint(0, 255)}.{random.randint(1, 254)}" for _ in range(256)]

# Runtime context
ctx = {
    "account_ids": [],
    "account_ibans": [],
    "transfer_ids": [],
}

# User pool for load tests (Test 3, 4)
# Each entry: {"token": str, "user_id": int, "account_ids": list, "account_ibans": list, "transfer_ids": list}
user_pool = []


# ==============================================================================
# HELPERS
# ==============================================================================

def print_header(title):
    print("\n" + "=" * 60)
    print(f" {title} ".center(60, "="))
    print("=" * 60)


def get_auth_token(username, password, source_ip=None):
    url = f"{BASE_URL}/api/v1/auth/login"
    ip = source_ip or _random_ip()
    headers = {"Content-Type": "application/json", "X-Forwarded-For": ip}
    try:
        r = requests.post(url, json={"username": username, "password": password}, headers=headers)
        if r.status_code == 200:
            data = r.json()
            return data.get("token"), data.get("userId")
        print(f"Token failed: {r.status_code} – {r.text}")
    except Exception as e:
        print(f"Auth error: {e}")
    return None, None


def _random_ip():
    return random.choice(IP_POOL)


def _random_amount():
    return round(random.uniform(*TRANSFER_AMOUNT_RANGE), 2)


_validation_errors = {}

EXPECTED_STATUSES = {
    "GET /accounts": 200,
    "GET /accounts/{id}": 200,
    "GET /accounts/iban/{iban}": 200,
    "GET /transfers/{id}": 200,
    "GET /transfers/history": 200,
    "GET /transfers/report": 200,
    "POST /auth/register": 201,
    "POST /transfers": 201,
    "POST /accounts": 201,
    "POST /transfers/{id}/cancel": 204,
}


def _is_success(endpoint, status):
    """Only the documented successful outcome of the requested operation counts."""
    return status == EXPECTED_STATUSES.get(endpoint)


def _percentile(sorted_values, fraction):
    """Nearest-rank percentile; callers sort successful request latencies first."""
    return sorted_values[max(0, math.ceil(len(sorted_values) * fraction) - 1)] if sorted_values else float("inf")


def _page_items(response):
    data = response.json()
    if not isinstance(data, dict) or not isinstance(data.get("content"), list):
        raise ValueError("Expected a paginated response with a content list")
    return data["content"]


def _create_account(token, user_id, owner_name, initial_balance):
    iban = f"TR{random.randint(0, 10**24 - 1):024d}"
    response = requests.post(
        f"{BASE_URL}/api/v1/accounts",
        json={"userId": user_id, "ownerName": owner_name, "iban": iban,
              "initialBalance": initial_balance, "currency": TRANSFER_CURRENCY},
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json",
                 "Idempotency-Key": str(uuid.uuid4()), "X-Forwarded-For": _random_ip()},
        timeout=(3, 10),
    )
    if response.status_code != 201:
        raise RuntimeError(f"Account bootstrap failed: HTTP {response.status_code} {response.text[:120]}")
    return response.json()

def _validate_response(url_label, status_code, body, expected_fields=None):
    if status_code == "ERROR":
        return
    if expected_fields and 200 <= status_code < 300:
        items = []
        if isinstance(body, dict):
            if "content" in expected_fields:
                missing = [f for f in expected_fields if f not in body]
                if missing:
                    key = f"{url_label}: missing {missing}"
                    _validation_errors[key] = _validation_errors.get(key, 0) + 1
                return
            if "content" in body and isinstance(body["content"], list):
                items = body["content"]
            else:
                items = [body]
        elif isinstance(body, list):
            items = body
        if items and isinstance(items[0], dict):
            missing = [f for f in expected_fields if f not in items[0]]
            if missing:
                key = f"{url_label}: missing {missing}"
                _validation_errors[key] = _validation_errors.get(key, 0) + 1


def _status_line(statuses, total=None):
    parts = []
    for s in sorted(statuses.keys(), key=lambda x: str(x)):
        c = statuses[s]
        if total:
            parts.append(f"{s}:{c}({c/total*100:.0f}%)")
        else:
            parts.append(f"{s}:{c}")
    return "  ".join(parts)


def _print_phase_result(phase_label, dur, rps, avg, lat_p50, error_cnt, statuses, total_req, ep_stats, lat_p95=None):
    line = f"  {phase_label}  {dur:.2f}s  {rps:.0f} rps  avg={avg:.0f}ms  p50={lat_p50:.0f}ms"
    if lat_p95 is not None:
        line += f"  p95={lat_p95:.0f}ms"
    line += f"  err={error_cnt}"
    print(line)
    if ep_stats:
        for ep in sorted(ep_stats.keys()):
            codes = ep_stats[ep]
            parts = " ".join(f"{s}:{codes[s]}" for s in sorted(codes.keys(), key=str))
            print(f"    {ep:<28s} {parts}")
    if _validation_errors:
        for key, count in sorted(_validation_errors.items()):
            print(f"    ! {key} ({count}x)")


def bootstrap_context(token):
    """Load context for the main user — used by Test 2."""
    global SENDER_IBAN, RECEIVER_IBAN
    headers = {"Authorization": f"Bearer {token}", "X-Forwarded-For": AUTH_IP}

    r = requests.get(f"{BASE_URL}/api/v1/accounts", headers=headers)
    if r.status_code == 200:
        accounts = _page_items(r)
        ctx["account_ids"]   = [a["id"]   for a in accounts if "id"   in a]
        ctx["account_ibans"] = [a["iban"] for a in accounts if "iban" in a]

    sender_account_id = None
    if len(ctx["account_ibans"]) >= 2:
        SENDER_IBAN   = ctx["account_ibans"][0]
        RECEIVER_IBAN = ctx["account_ibans"][1]
        sender_account_id = ctx["account_ids"][0]
    elif len(ctx["account_ibans"]) == 1:
        SENDER_IBAN   = ctx["account_ibans"][0]
        RECEIVER_IBAN = ctx["account_ibans"][0]
        sender_account_id = ctx["account_ids"][0]
    else:
        print(" WARNING: User has no accounts! Transfer tests will be skipped.")

    query_ids = ([sender_account_id] if sender_account_id else []) + [
        aid for aid in ctx["account_ids"][:3] if aid != sender_account_id
    ]
    for acc_id in query_ids[:3]:
        r = requests.get(f"{BASE_URL}/api/v1/transfers/history/{acc_id}", headers=headers)
        if r.status_code == 200:
            items = _page_items(r)
            if isinstance(items, list):
                ctx["transfer_ids"] += [t["id"] for t in items if "id" in t]

    ctx["transfer_ids"] = list(set(ctx["transfer_ids"]))[:20]

    print(f"Context: account_ids={ctx['account_ids']}, ibans(first 2)={ctx['account_ibans'][:2]}, "
          f"transfer_ids(first 5)={ctx['transfer_ids'][:5]}")
    if SENDER_IBAN:
        print(f"Using own account for transfer tests: sender={SENDER_IBAN}, receiver={RECEIVER_IBAN}")


def _create_single_user(i):
    """Create one load test user: register, login, create account, fetch accounts.
       Returns a user dict on success, None on failure.
       Each request uses a random source IP to spread rate-limit across IPs."""
    uname = f"ldu_{i}_{uuid.uuid4().hex[:6]}"
    password = "TestPass123!"
    reg_ip = _random_ip()

    for attempt in range(3):
        try:
            reg_headers = {"Content-Type": "application/json", "X-Forwarded-For": reg_ip}
            r = requests.post(f"{BASE_URL}/api/v1/auth/register",
                              json={"username": uname, "password": password},
                              headers=reg_headers)
            if r.status_code == 201:
                break
            if r.status_code == 429:
                time.sleep(12)
                reg_ip = _random_ip()
                continue
            if r.status_code == 409:
                return None
            return None
        except Exception as e:
            print(f"  [{i}] Register error: {e}")
            return None
    else:
        return None

    login_ip = _random_ip()
    for attempt in range(3):
        user_token, uid = get_auth_token(uname, password, source_ip=login_ip)
        if user_token:
            break
        time.sleep(12)
        login_ip = _random_ip()
    if not user_token:
        return None

    try:
        first = _create_account(user_token, uid, uname, 50000.00)
        second = _create_account(user_token, uid, uname, 50000.00)
        seed = requests.post(
            f"{BASE_URL}/api/v1/transfers",
            json={"senderIban": first["iban"], "receiverIban": second["iban"],
                  "amount": 1.00, "currency": TRANSFER_CURRENCY},
            headers={"Authorization": f"Bearer {user_token}", "Content-Type": "application/json",
                     "Idempotency-Key": str(uuid.uuid4()), "X-Forwarded-For": _random_ip()},
            timeout=(3, 10),
        )
        if seed.status_code != 201:
            raise RuntimeError(f"Transfer bootstrap failed: HTTP {seed.status_code} {seed.text[:120]}")
    except Exception as exc:
        print(f"  [{i}] Bootstrap error: {exc}")
        return None

    try:
        r = requests.get(f"{BASE_URL}/api/v1/accounts",
                         headers={"Authorization": f"Bearer {user_token}", "X-Forwarded-For": _random_ip()})
        if r.status_code == 200:
            accounts = _page_items(r)
            print(f"  [{i}] ok ({uname})")
            return {
                "token": user_token,
                "user_id": uid,
                "account_ids": [a["id"] for a in accounts if "id" in a],
                "account_ibans": [a["iban"] for a in accounts if "iban" in a],
                "transfer_ids": [seed.json()["id"]],
                "cancellable_transfer_ids": [seed.json()["id"]],
                "transfer_ids_lock": threading.Lock(),
            }
        print(f"  [{i}] Account lookup failed: HTTP {r.status_code}")
        return None
    except Exception as exc:
        print(f"  [{i}] Account lookup error: {exc}")
        return None


def bootstrap_users():
    """Create NUM_LOAD_USERS users in parallel using ThreadPoolExecutor.
       Each user uses random source IPs to spread auth rate-limit across IPs."""
    global user_pool
    print(f"\nCreating {NUM_LOAD_USERS} load test users... (concurrency={BOOTSTRAP_CONCURRENCY})")
    user_pool = []
    completed = 0
    with ThreadPoolExecutor(max_workers=BOOTSTRAP_CONCURRENCY) as ex:
        futures = {ex.submit(_create_single_user, i): i for i in range(NUM_LOAD_USERS)}
        for f in as_completed(futures):
            i = futures[f]
            result = f.result()
            if result:
                user_pool.append(result)
            completed += 1
            if completed % 5 == 0 or completed == NUM_LOAD_USERS:
                print(f"  ... {completed}/{NUM_LOAD_USERS} users processed")
    user_pool.sort(key=lambda u: u["user_id"] or 0)
    print(f"  Total {len(user_pool)}/{NUM_LOAD_USERS} users ready.")
    if user_pool:
        sample = user_pool[0]
        print(f"  Sample: user_id={sample['user_id']}, accounts={len(sample['account_ids'])}")


def get_account_balances(token):
    headers = {"Authorization": f"Bearer {token}", "X-Forwarded-For": AUTH_IP}
    r = requests.get(f"{BASE_URL}/api/v1/accounts", headers=headers)
    if r.status_code == 200:
        return {a["iban"]: {"balance": a.get("balance"), "currency": a.get("currency")} for a in _page_items(r)}
    return {}


# ==============================================================================
# TEST 1 – RATE LIMIT
# ==============================================================================

def test_rate_limiting():
    print_header("1. RATE LIMITING TEST")
    url = f"{BASE_URL}/api/v1/auth/register"
    print(f"IP: {RATE_LIMIT_TEST_IP} – Sending 15 requests (limit: 10 req / 10 sec)...")

    results = []
    headers = {"Content-Type": "application/json", "X-Forwarded-For": RATE_LIMIT_TEST_IP}

    for i in range(1, 16):
        uname = f"user_{uuid.uuid4().hex[:8]}"
        try:
            t0 = time.time()
            r  = requests.post(url, json={"username": uname, "password": "Password123!"}, headers=headers)
            dur = (time.time() - t0) * 1000
            results.append((i, r.status_code, r.text, dur, uname))
        except Exception as e:
            results.append((i, "ERROR", str(e), 0, uname))
        time.sleep(0.05)

    success_cnt     = sum(1 for r in results if r[1] == 201)
    rate_limit_cnt  = sum(1 for r in results if r[1] == 429)

    for idx, status, body, dur, uname in results:
        print(f"  #{idx:02d} ({uname}): {status} | {dur:.0f}ms | {body[:70]}")

    print(f"\nResult: {success_cnt} succeeded, {rate_limit_cnt} blocked (429)")
    if rate_limit_cnt > 0:
        print(" PASS: Rate limiter is working.")
    else:
        print(" FAIL: Rate limiter did not engage.")

    print("\nWaiting 11 seconds for window reset...")
    time.sleep(11)
    uname = f"user_{uuid.uuid4().hex[:8]}"
    r = requests.post(url, json={"username": uname, "password": "Password123!"}, headers=headers)
    print(f"Post-cooldown request: {r.status_code} (expected: 201)")
    print(" PASS: Window reset." if r.status_code == 201 else " FAIL: Window did not reset.")
    return rate_limit_cnt > 0 and r.status_code == 201


# ==============================================================================
# TEST 2 – CONCURRENCY & IDEMPOTENCY
# ==============================================================================

def test_concurrency_and_idempotency(token):
    print_header("2. CONCURRENCY & IDEMPOTENCY TEST")

    if SENDER_IBAN is None or RECEIVER_IBAN is None:
        print(" SKIP: No usable accounts for the user, skipping test.")
        return False

    balances_before = get_account_balances(token)
    print(f"Sender balance ({SENDER_IBAN}): {balances_before.get(SENDER_IBAN)}")
    print(f"Receiver balance ({RECEIVER_IBAN}):   {balances_before.get(RECEIVER_IBAN)}")

    # --- Phase A: Idempotency ---
    print("\n--- Phase A: 5 Concurrent Requests with Same Idempotency-Key ---")
    ikey = str(uuid.uuid4())
    payload = {"senderIban": SENDER_IBAN, "receiverIban": RECEIVER_IBAN,
               "amount": 10.00, "currency": TRANSFER_CURRENCY}

    def send_transfer(key, pld, ip):
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Idempotency-Key": key,
            "X-Forwarded-For": ip,
        }
        try:
            r = requests.post(f"{BASE_URL}/api/v1/transfers", json=pld, headers=headers)
            return r.status_code, r.text
        except Exception as e:
            return "ERROR", str(e)

    with ThreadPoolExecutor(max_workers=5) as ex:
        futures = [ex.submit(send_transfer, ikey, payload, f"192.168.3.{10+i}") for i in range(5)]
        phase_a = [f.result() for f in futures]

    for i, (s, b) in enumerate(phase_a, 1):
        print(f"  Req #{i}: {s} | {b[:90]}")

    bal_a = get_account_balances(token)
    created_ids = [json.loads(body)["id"] for status, body in phase_a if status == 201]
    phase_a_statuses_valid = all(status in (201, 409) for status, _ in phase_a)
    sender_before = float(balances_before[SENDER_IBAN]["balance"])
    sender_after = float(bal_a[SENDER_IBAN]["balance"])
    idempotent = (phase_a_statuses_valid and len(created_ids) >= 1
                  and len(set(created_ids)) == 1
                  and abs((sender_before - sender_after) - 10.0) < 0.01)
    print(f"Balance (after): {bal_a.get(SENDER_IBAN)}")
    print(f"Created response IDs: {created_ids}")
    print(" PASS: Idempotency works!" if idempotent else " FAIL: Duplicate or missing transfer effect!")

    # --- Phase B: Race Condition ---
    print("\n--- Phase B: Concurrent Overdraft Test ---")
    cur_bal = float(bal_a.get(SENDER_IBAN, {}).get("balance", 0))
    amount  = round(cur_bal / 3, 2)
    n       = 10
    print(f"Current balance: {cur_bal} {TRANSFER_CURRENCY}")
    print(f"{n} concurrent transfers x {amount} {TRANSFER_CURRENCY} = {n*amount} {TRANSFER_CURRENCY} requested")

    def send_unique(idx):
        return send_transfer(str(uuid.uuid4()),
                             {"senderIban": SENDER_IBAN, "receiverIban": RECEIVER_IBAN,
                              "amount": amount, "currency": TRANSFER_CURRENCY},
                             f"192.168.4.{10+idx}")

    with ThreadPoolExecutor(max_workers=n) as ex:
        phase_b = [f.result() for f in [ex.submit(send_unique, i) for i in range(n)]]

    ok_b   = sum(1 for s, _ in phase_b if s == 201)
    fail_b = n - ok_b
    final  = get_account_balances(token)
    final_bal = float(final.get(SENDER_IBAN, {}).get("balance", 0))

    for i, (s, b) in enumerate(phase_b, 1):
        print(f"  Req #{i:02d}: {s} | {b[:90]}")

    exp_red = ok_b * amount
    act_red = cur_bal - final_bal
    print(f"\nFinal balance:     {final_bal} {TRANSFER_CURRENCY}")
    print(f"Successful tx:     {ok_b}")
    print(f"Failed tx:         {fail_b}")
    print(f"Expected decrease: {exp_red} | Actual decrease: {act_red}")

    balance_consistent = abs(exp_red - act_red) < 0.01
    if balance_consistent:
        print(" PASS: Balance consistent!")
    else:
        print(" FAIL: Balance mismatch – race condition detected!")
    print(" PASS: Balance did not go negative." if final_bal >= 0
          else " CRITICAL FAIL: Balance went negative!")
    return idempotent and balance_consistent and final_bal >= 0 and ok_b < n


# ==============================================================================
# SHARED: worker factory – covers all controller endpoints
# ==============================================================================

def _build_thresholds():
    T = [0]
    for r in [R_ACCOUNTS_LIST, R_ACCOUNT_BY_ID, R_ACCOUNT_BY_IBAN,
              R_TRANSFER_DETAIL, R_TRANSFER_HISTORY, R_TRANSFER_REPORT,
              W_REGISTER, W_TRANSFER, W_CREATE_ACCOUNT, W_CANCEL_TRANSFER]:
        T.append(T[-1] + r)
    return T


THRESHOLDS = _build_thresholds()


def make_worker(pool, think_time=None):
    """
    pool: list from user_pool – picks a random user on each call.
    think_time: (min, max) seconds to sleep after each request. None = no think time.
    Returned worker signature: worker(index) -> (status_code, duration_ms, endpoint)
    """

    def _pick_user():
        if not pool:
            return None
        return random.choice(pool)

    def _think():
        if think_time:
            time.sleep(random.uniform(*think_time))

    def worker(index):
        user = _pick_user()
        if user is None:
            return "ERROR", 0, "__no_user__"

        token = user["token"]
        thread_ip = _random_ip()
        roll = random.random()
        ep = ""

        try:
            start = time.time()
            _req = lambda m, u, **kw: requests.request(m, u, timeout=(3, 10), **kw)

            if roll < THRESHOLDS[1]:
                ep = "GET /accounts"
                r = _req("GET", f"{BASE_URL}/api/v1/accounts",
                                headers={"Authorization": f"Bearer {token}",
                                         "X-Forwarded-For": thread_ip})
                if r.status_code == 200:
                    _validate_response(ep, r.status_code, r.json() if r.text else {},
                                       expected_fields=["id", "iban", "balance"])

            elif roll < THRESHOLDS[2]:
                ep = "GET /accounts/{id}"
                if not user["account_ids"]:
                    return "SKIP", 0, ep
                acc_id = random.choice(user["account_ids"])
                r = _req("GET", f"{BASE_URL}/api/v1/accounts/{acc_id}",
                                headers={"Authorization": f"Bearer {token}",
                                         "X-Forwarded-For": thread_ip})
                if r.status_code == 200:
                    _validate_response(ep, r.status_code, r.json(),
                                       expected_fields=["id", "iban", "balance"])

            elif roll < THRESHOLDS[3]:
                ep = "GET /accounts/iban/{iban}"
                if not user["account_ibans"]:
                    return "SKIP", 0, ep
                iban = random.choice(user["account_ibans"])
                r = _req("GET", f"{BASE_URL}/api/v1/accounts/iban/{iban}",
                                headers={"Authorization": f"Bearer {token}",
                                         "X-Forwarded-For": thread_ip})

            elif roll < THRESHOLDS[4]:
                ep = "GET /transfers/{id}"
                with user["transfer_ids_lock"]:
                    tid = random.choice(user["transfer_ids"]) if user["transfer_ids"] else None
                if tid is None:
                    return "SKIP", 0, ep
                r = _req("GET", f"{BASE_URL}/api/v1/transfers/{tid}",
                                headers={"Authorization": f"Bearer {token}",
                                         "X-Forwarded-For": thread_ip})
                if r.status_code == 200:
                    _validate_response(ep, r.status_code, r.json(),
                                       expected_fields=["id", "amount", "status"])

            elif roll < THRESHOLDS[5]:
                ep = "GET /transfers/history"
                if not user["account_ids"]:
                    return "SKIP", 0, ep
                acc_id = random.choice(user["account_ids"])
                r = _req("GET", f"{BASE_URL}/api/v1/transfers/history/{acc_id}",
                                headers={"Authorization": f"Bearer {token}",
                                         "X-Forwarded-For": thread_ip})
                if r.status_code == 200:
                    data = r.json()
                    _validate_response(ep, r.status_code, data,
                                       expected_fields=["content", "totalElements"])

            elif roll < THRESHOLDS[6]:
                ep = "GET /transfers/report"
                if not user["account_ids"]:
                    return "SKIP", 0, ep
                acc_id = random.choice(user["account_ids"])
                end_dt   = datetime.datetime.now()
                start_dt = end_dt - datetime.timedelta(days=180)
                params = {
                    "accountId": acc_id,
                    "startDate": start_dt.strftime("%Y-%m-%dT%H:%M:%S"),
                    "endDate":   end_dt.strftime("%Y-%m-%dT%H:%M:%S"),
                }
                r = _req("GET", f"{BASE_URL}/api/v1/transfers/report", params=params,
                                headers={"Authorization": f"Bearer {token}",
                                         "X-Forwarded-For": thread_ip})

            elif roll < THRESHOLDS[7]:
                ep = "POST /auth/register"
                uname = f"u_{uuid.uuid4().hex[:10]}_{index}"
                r = _req("POST", f"{BASE_URL}/api/v1/auth/register",
                                 json={"username": uname, "password": "Password123!"},
                                 headers={"Content-Type": "application/json",
                                          "X-Forwarded-For": thread_ip})

            elif roll < THRESHOLDS[8]:
                ep = "POST /transfers"
                if len(user["account_ibans"]) < 2:
                    return "SKIP", 0, ep
                amount = _random_amount()
                sender = user["account_ibans"][0]
                receiver = user["account_ibans"][1]
                r = _req("POST", f"{BASE_URL}/api/v1/transfers",
                                 json={"senderIban": sender, "receiverIban": receiver,
                                       "amount": amount, "currency": TRANSFER_CURRENCY},
                                 headers={"Authorization": f"Bearer {token}",
                                          "Content-Type": "application/json",
                                          "Idempotency-Key": str(uuid.uuid4()),
                                          "X-Forwarded-For": thread_ip})
                if r.status_code == 201:
                    transfer_id = r.json()["id"]
                    with user["transfer_ids_lock"]:
                        user["transfer_ids"].append(transfer_id)
                        user["cancellable_transfer_ids"].append(transfer_id)

            elif roll < THRESHOLDS[9]:
                ep = "POST /accounts"
                digits = f"{random.randint(0, 10**24 - 1):024d}"
                ibanr  = f"TR{digits}"
                r = _req("POST", f"{BASE_URL}/api/v1/accounts",
                                 json={
                                     "userId": user["user_id"],
                                     "ownerName": f"Load Test User {index}",
                                     "iban": ibanr,
                                     "initialBalance": 100.0,
                                     "currency": TRANSFER_CURRENCY,
                                 },
                                 headers={"Authorization": f"Bearer {token}",
                                          "Content-Type": "application/json",
                                          "X-Forwarded-For": thread_ip})

            else:
                ep = "POST /transfers/{id}/cancel"
                with user["transfer_ids_lock"]:
                    tid = user["cancellable_transfer_ids"].pop() if user["cancellable_transfer_ids"] else None
                if tid is None:
                    return "SKIP", 0, ep
                r = _req("POST", f"{BASE_URL}/api/v1/transfers/{tid}/cancel",
                                 headers={"Authorization": f"Bearer {token}",
                                          "Idempotency-Key": str(uuid.uuid4()),
                                          "X-Forwarded-For": thread_ip})
                if r.status_code != 204:
                    with user["transfer_ids_lock"]:
                        user["cancellable_transfer_ids"].append(tid)

            duration = (time.time() - start) * 1000
            _think()
            return r.status_code, duration, ep

        except requests.exceptions.Timeout:
            return "TIMEOUT", 0, ep
        except requests.exceptions.ConnectionError:
            return "CONN_ERR", 0, ep
        except Exception:
            return "ERROR", 0, ep

    return worker


def _print_workload_legend():
    print(f"\n  Workload Distribution:")
    print(f"  {'Ratio':>6}  Endpoint")
    print(f"  {'------':>6}  " + "-" * 46)
    for ratio, label in [
        (R_ACCOUNTS_LIST,    "GET  /api/v1/accounts"),
        (R_ACCOUNT_BY_ID,    "GET  /api/v1/accounts/{id}"),
        (R_ACCOUNT_BY_IBAN,  "GET  /api/v1/accounts/iban/{iban}"),
        (R_TRANSFER_DETAIL,  "GET  /api/v1/transfers/{id}"),
        (R_TRANSFER_HISTORY, "GET  /api/v1/transfers/history/{accountId}"),
        (R_TRANSFER_REPORT,  "GET  /api/v1/transfers/report"),
        (W_REGISTER,         "POST /api/v1/auth/register"),
        (W_TRANSFER,         "POST /api/v1/transfers"),
        (W_CREATE_ACCOUNT,   "POST /api/v1/accounts"),
        (W_CANCEL_TRANSFER,  "POST /api/v1/transfers/{id}/cancel"),
    ]:
        print(f"  {ratio*100:>5.0f}%  {label}")
    if THINK_TIME_RANGE[1] > 0:
        print(f"\n  Think time: {THINK_TIME_RANGE[0]}–{THINK_TIME_RANGE[1]} sec")
    print(f"  User count: {len(user_pool)}")


# ==============================================================================
# TEST 3 – STATIC LOAD TEST (with ramp-up)
# ==============================================================================

def _run_phase(pool, concurrency, label, duration=None, req_count=None, think_time=None):
    """Run one load phase. Either duration (seconds) or req_count must be set."""
    worker = make_worker(pool, think_time=think_time)
    latencies = []
    statuses  = {}
    ep_stats = {}

    t0 = time.time()
    idx = 0
    total = 0
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        while True:
            batch = [ex.submit(worker, idx + i) for i in range(concurrency)]
            for f in as_completed(batch):
                status, dur, ep = f.result()
                if _is_success(ep, status):
                    latencies.append(dur)
                statuses[status] = statuses.get(status, 0) + 1
                if ep:
                    ep_stats.setdefault(ep, {})
                    ep_stats[ep][status] = ep_stats[ep].get(status, 0) + 1
                total += 1
            idx += concurrency
            if duration is not None and time.time() - t0 >= duration:
                break
            if req_count is not None and idx >= req_count:
                break

    dur = time.time() - t0
    rps = len(latencies) / dur if dur > 0 else 0
    avg = sum(latencies) / len(latencies) if latencies else 0
    latencies.sort()
    p50 = _percentile(latencies, 0.50)
    p95 = _percentile(latencies, 0.95)
    err_cnt = total - len(latencies)
    _print_phase_result(label, dur, rps, avg, p50, err_cnt, statuses, total, ep_stats, lat_p95=p95)
    _validation_errors.clear()
    return total, latencies, statuses


def test_load(pool):
    print_header("3. HTTP LOAD TEST – All Controllers (with Ramp-up)")
    _print_workload_legend()

    all_latencies  = []
    all_statuses   = {}

    # Warm-up
    print(f"\n  Warm-up: {WARMUP_REQUESTS} requests at concurrency {WARMUP_CONCURRENCY} (no think time)")
    _run_phase(pool, WARMUP_CONCURRENCY, "warm-up",
               req_count=WARMUP_REQUESTS, think_time=None)

    # Ramp-up phases (sustained load with think time)
    print(f"\n  Ramp-up phases (think={THINK_TIME_RANGE[0]}–{THINK_TIME_RANGE[1]}s, min {MIN_PHASE_DURATION}s each):")
    total_start = time.time()
    total_req = 0
    for phase_idx, (phase_con, phase_label) in enumerate(RAMP_UP_PHASES):
        label = f"Phase {phase_idx+1}/{len(RAMP_UP_PHASES)} ({phase_label})"
        count, lat, stat = _run_phase(pool, phase_con, label,
                                       duration=MIN_PHASE_DURATION, think_time=THINK_TIME_RANGE)
        total_req += count
        all_latencies.extend(lat)
        for k, v in stat.items():
            all_statuses[k] = all_statuses.get(k, 0) + v

    total_dur = time.time() - total_start

    all_latencies.sort()
    overall_avg = sum(all_latencies) / len(all_latencies) if all_latencies else 0
    p50 = _percentile(all_latencies, 0.50)
    p95 = _percentile(all_latencies, 0.95)
    p99 = _percentile(all_latencies, 0.99)

    print(f"  === OVERALL ===")
    print(f"  {total_req} req  {total_dur:.2f}s  {len(all_latencies)/total_dur:.0f} successful rps  errors={total_req-len(all_latencies)}  avg={overall_avg:.0f}ms  p50={p50:.0f}ms  p95={p95:.0f}ms  p99={p99:.0f}ms")
    print(f"  {_status_line(all_statuses, total_req)}")


# ==============================================================================
# TEST 4 – CAPACITY FINDER (with user pool)
# ==============================================================================

def find_max_throughput_under_latency(pool, target_ms=100.0):
    print_header(f"4. CAPACITY TEST – All Controllers (Target p95 <= {target_ms} ms)")
    print(f"  Same workload as Test 3  |  User count: {len(pool)}")

    best_c, best_rps, best_lat = None, 0.0, 0.0
    if not pool:
        print("RESULT: No load users were bootstrapped.")
        return False

    for c in CAPACITY_TEST_CONCURRENCY_STEPS:
        req_count  = min(max(200, c * 3), 3000)
        worker     = make_worker(pool)
        latencies  = []
        status_codes = {}
        ep_stats = {}

        t0 = time.time()
        with ThreadPoolExecutor(max_workers=c) as ex:
            futs = [ex.submit(worker, idx) for idx in range(req_count)]
            for f in as_completed(futs):
                status, dur, ep = f.result()
                if _is_success(ep, status):
                    latencies.append(dur)
                status_codes[status] = status_codes.get(status, 0) + 1
                if ep:
                    ep_stats.setdefault(ep, {})
                    ep_stats[ep][status] = ep_stats[ep].get(status, 0) + 1

        total_dur  = time.time() - t0
        rps        = len(latencies) / total_dur if total_dur > 0 else 0
        avg        = sum(latencies) / len(latencies) if latencies else 9999
        latencies.sort()
        p50        = _percentile(latencies, 0.50)
        p95        = _percentile(latencies, 0.95)
        p99        = _percentile(latencies, 0.99)

        ok = len(latencies)
        success_rate = ok / req_count * 100 if req_count > 0 else 0

        verdict = "PASS"
        if success_rate < 99.0:
            verdict = "FAIL (Errors)"
        elif p95 > target_ms:
            verdict = "FAIL (p95 Latency)"

        err_cnt = req_count - ok
        label = f"c={c}"
        show_ep = verdict != "PASS"
        _print_phase_result(label, total_dur, rps, avg, p50, err_cnt, status_codes, req_count, ep_stats if show_ep else None, lat_p95=p95)
        print(f"    p99={p99:.0f}ms  success={success_rate:.2f}%  {verdict}")
        _validation_errors.clear()

        if verdict == "PASS":
            best_c, best_rps, best_lat = c, rps, p95
        else:
            print(f"  >> Threshold breached at concurrency {c}")
            break

        time.sleep(1)

    print("=" * 80)
    if best_c:
        print(f"RESULT: Highest passing tested level (p95 <= {target_ms}ms, success >= 99%):")
        print(f"  Successful throughput: {best_rps:.2f} req/sec")
        print(f"  Concurrency:   {best_c} threads")
        print(f"  p95 Latency:   {best_lat:.1f} ms")
    else:
        print(f"RESULT: No concurrency level passed p95 <= {target_ms}ms and success >= 99%.")
    return best_c is not None


# ==============================================================================
# MAIN
# ==============================================================================

if __name__ == "__main__":
    import traceback
    try:
        # 1. Rate Limiting Test
        rate_limit_passed = test_rate_limiting()

        # 3. Create load test users
        bootstrap_users()
        if len(user_pool) != NUM_LOAD_USERS:
            raise RuntimeError(
                f"Only {len(user_pool)}/{NUM_LOAD_USERS} load users could be bootstrapped; "
                "capacity figures would use a different workload")

        # 4. Load a prepared user context (for Test 2)
        print("\nLoading prepared user context (account and transfer IDs)...")
        token = user_pool[0]["token"]
        bootstrap_context(token)

        # 5. Concurrency & Idempotency
        consistency_passed = test_concurrency_and_idempotency(token)

        # 6. Static Load (with user_pool, ramp-up)
        test_load(user_pool)

        # 7. Capacity Finder (with user_pool)
        capacity_passed = find_max_throughput_under_latency(user_pool, CAPACITY_TEST_TARGET_LATENCY_MS)
        if not (rate_limit_passed and consistency_passed and capacity_passed):
            raise RuntimeError("At least one load-test acceptance check failed")

    except Exception as e:
        print(f"\n[ERROR] Unexpected error: {e}")
        traceback.print_exc()
        exit(1)
