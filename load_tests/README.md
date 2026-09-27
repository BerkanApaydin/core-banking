# Load and rate-limit checks

Run these only against an isolated simulation database. The scripts register users,
open funded accounts and create or cancel transfers; they are not read-only smoke
checks. From the repository root, install the client dependency with
`python -m pip install -r load_tests/requirements.txt`.

The two modes require different server settings and therefore run separately:

1. Start the application with its normal limit of 10 requests per 10 seconds.
   Run `python load_tests/runner.py --mode rate-limit`. This mode sends 15
   registration requests from the real client address and checks for HTTP 429,
   then checks that a request succeeds after the window expires. It creates
   test users. Avoid other traffic from the same address during this check.
2. On an **isolated** `dev` or `prod,simulation` deployment, raise
   `RATE_LIMIT_MAX_REQUESTS` to a value above the expected test traffic, then
   restart the app. For example, for a local Compose deployment in PowerShell:

   ```powershell
   $env:RATE_LIMIT_MAX_REQUESTS = '100000'
   docker compose up -d --force-recreate app
   python load_tests/runner.py --mode workload
   Remove-Item Env:RATE_LIMIT_MAX_REQUESTS
   docker compose up -d --force-recreate app
   ```

   Workload mode verifies the account
   funding capability before opening its first account, then creates 50 users
   with two funded accounts and a seed transfer each. It runs concurrency,
   idempotency, ramp-up and capacity checks. Treat any HTTP 429 as a capacity
   configuration problem, not as application throughput. The measured p95 and
   request rate depend on the chosen host, data set and deployment settings.

`TARGET_URL` defaults to `http://localhost:8080` for host-side Python. If using
the Docker image (`docker build -t bank-load-test load_tests`), pass an explicit
`TARGET_URL` reachable **from the container** and a `--mode`; container-local
`localhost` does not refer to the application. Running the image with no mode
prints usage instead of creating test data. Never enable
`PROXY_TRUST_HEADERS=true` merely to make load requests appear to come from
different IPs: that setting is only for a trusted proxy that overwrites the
header.

The offline checks are `python -m unittest load_tests.test_runner -v`.
They verify the client contract and result classification, but do not measure
a running deployment. The read-only deployment check is
`python ops/health_smoke.py http://localhost:8080`.
