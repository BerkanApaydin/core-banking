# Operational checks and recovery evidence

This runbook describes the checked-in application and deployment templates. Passing the smoke check below proves only the listed HTTP contracts at that moment. It does not certify financial correctness, Redis revocation durability, restore capability, capacity or production readiness. Run fault injection and recovery drills in an isolated staging environment with synthetic accounts.

CI runs a sequential Maven `clean install` (including `verify`) before the per-module coverage gate.
The install phase publishes reactor JARs and the shared test JAR to the runner's local Maven repository so the separate, parallel PIT invocation can resolve them.
Pull requests also run GitHub dependency review, which rejects newly introduced
high/critical advisories, and the build publishes an aggregate CycloneDX SBOM
(`cyclonedx-sbom` artifact). Dependency review checks changes in a PR, so the
existing dependency baseline still needs a scheduled security review. The SBOM
lists components; it does not itself test whether they are vulnerable.

## Read-only deployment smoke check

From the repository root, with Python 3.10 or newer:

```text
python ops/health_smoke.py http://localhost:8080
python ops/health_smoke.py https://your-staging-host --require-probes --json
python -m unittest ops.test_health_smoke -v
```

The script sends only unauthenticated GET requests. It requires HTTP 200 and JSON `status: UP` from `/actuator/health`, and HTTP 401 with a Problem Detail JSON status of 401 from `/api/v1/auth/browser/session`. The session path sits on the auth rate-limit tier (10/10s per IP), so the smoke check's single request cannot 429 under normal conditions — but do not loop it in a tight retry. `--require-probes` also requires `/actuator/health/liveness` and `/actuator/health/readiness` to return HTTP 200 with `status: UP`. Use it for the Kubernetes deployment; the `prod` profile enables these probe groups (`management.endpoint.health.probes.enabled`), so every prod deployment — including Kubernetes — serves them. Outside `prod`, enable them explicitly if needed (`MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`). A missing required endpoint fails the check. The script does not change that setting.

Redirects, HTML login pages, malformed/oversized responses, DOWN status and network errors fail the check. Exit code 0 means all selected checks passed, 1 means a check failed, and 2 means invalid command arguments. `--timeout` is a socket timeout per request, not a total deployment deadline. Output does not include response bodies or credentials. Save `--json` output with the release identifier and check time as rollout evidence.

The production readiness group includes `readinessState`, `db`, and the custom `redisBackends` indicator. An UP readiness response therefore requires both backing stores to answer at that moment. The global health check is retained as an independent check; neither result proves data integrity or downstream delivery. Liveness remains a process-health signal so a shared dependency outage does not restart every replica. The Kubernetes startup probe gives the application up to five minutes to complete initialization; its 60-second termination grace allows the configured 30-second graceful shutdown plus routing/drain time. Measure these budgets in staging before relying on them.

`/actuator/prometheus` is exposed by Actuator configuration and whitelisted for
anonymous scraping **in the prod profile only** (`application-prod.yml`), where
`k8s/networkpolicy.yaml` isolates it to the `monitoring` namespace. The live
smoke (`health_smoke.py --require-metrics --allow-metrics-secured`) treats
401/403 as "deliberately gated" but fails on 404, so dropping the endpoint from
the exposure list breaks CI instead of silently killing every alert. The
regression is pinned by `ProductionConfigContractTest` in `app`. Non-prod
profiles keep the endpoint authentication-gated (`ActuatorSecurityIntegrationTest`
pins the 401/403 behavior there).

## Configuration and connection budget

| Deployment path | Required check |
|---|---|
| Local Java with Compose dependencies | Compose publishes Redis on host port **6389**. Set `REDIS_PORT=6389` for the host Java process; app containers correctly use `redis:6379`. |
| Compose database credentials | PostgreSQL service initializes `bank_user` / `bank_password`; app variables are overrideable. Overriding only app credentials will not rotate the database role. Existing database volumes retain their initialized credentials. Coordinate DB role and application secret changes; do not delete a volume to repair authentication. |
| Kubernetes | Supply referenced DB/JWT secrets and reachable PostgreSQL/Redis services. The template ships the versioned `bank-app:0.0.1` tag by default (`IfNotPresent`); every production apply must use a digest pinned by `scripts/pin-image-digest.sh` (or the `v*` tag release workflow) and recorded in `k8s/.image-digest` — see [release automation](release.md). CI fails if `bank-app:latest` reappears. Ingress/TLS, NetworkPolicy and Prometheus rules ship in `k8s/`; backup schedule is owned by the platform (see [disaster recovery](disaster-recovery.md)). |
| Redis security | The custom factory supports standalone host/port, database, username/password, TLS enabled and connection/command timeouts, plus a bounded Lettuce pool (`spring.data.redis.lettuce.pool.*`, `spring.data.redis.timeout` — every request shares the limiter connection, so an unbounded pool parks Tomcat threads on a slow command). Configure supported `spring.data.redis` properties through the deployment secret/config mechanism. Sentinel, cluster, URL and SSL bundle configurations are explicitly rejected. |
| Database connection capacity | Hikari permits 20 connections per replica by default (`DB_MAX_POOL_SIZE`, `application.yml`). HPA permits six replicas: 120 connections, or 140 with one rollout surge replica, before migrations, operators and other clients. Rule: `replicas x pool <= max_connections - headroom` — stock PostgreSQL 15 defaults to 100, so production needs a managed instance with `max_connections >= 200` or PgBouncer in transaction-pooling mode. Derive the pool limit from the actual database budget and measure pool wait/lock wait; these figures are configured ceilings, not measured usage. |
| Outbox partition budget | The outbox poller runs in every replica and claims rows with `SKIP LOCKED` (no leader election by design). Throughput scales with `app.outbox.partition-count` (default 2), not with replicas: adding pods without adding partitions only adds contending scanners against the same rows. Rule: `partition-count >= replicas x 2`, raised via the quiesce/drain procedure in the [outbox runbook](outbox-operations.md) — partition-count changes are not ordinary rolling changes. Measure pending age (`outbox.oldest_pending.age_seconds`) in staging before raising either side. |
| Client IP behind ingress | With `PROXY_TRUST_HEADERS=false` (default) the app uses the TCP peer address. Behind ingress-nginx, external clients share the ingress IP on the **auth tier** (`/api/v1/auth/*`, still IP-keyed brute-force protection — one abusive client 429s everyone there). Authenticated resource traffic (`/accounts`, `/transfers`, `/admin`) is keyed by principal (`PrincipalRateLimitingFilter`), so one NAT egress cannot starve legitimate users. The Kubernetes manifest sets `PROXY_TRUST_HEADERS=true`, which is correct only for the shipped single-ingress topology: ingress-nginx appends the downstream peer as the last `X-Forwarded-For` entry and the app trusts the last entry only. Never enable it behind a CDN/multi-hop chain or without an overwriting/appending edge proxy — a spoofed first entry would otherwise mint unlimited fresh buckets. |
| JVM-local security backends | `FAILED_LOGIN_BACKEND`, `RATE_LIMIT_BACKEND` and `TOKEN_BLACKLIST_BACKEND` default to `caffeine` (single-JVM, dev/test). Never combine HPA multi-replica with `caffeine`: brute-force budgets multiply per pod and a logout/revocation on one replica is invisible to the others for up to 60s. Production profiles enforce the shared backends at boot (`ApplicationStartupValidator`); any HPA deployment must keep `redis` (rate-limit/login) and `hybrid`/`database` (blacklist). |

The default profile is `prod`; production deployment should still select `prod` explicitly and supply valid secrets. Local development must select `dev` explicitly (the development scripts and Compose already do this). Production safety checks run before application singleton initialization: default secrets, non-durable token revocation, insecure browser cookies, process-local security limiters, non-positive limiter settings, and mixing `prod` with `dev`, `demo`, `test` or `testcontainers` stop startup. Flyway automatic baselining is disabled in production; adopting an existing non-empty schema requires a reviewed migration baseline. This application is a bank simulation: a hosted demo that needs simulated opening balances should select `prod,simulation`, preserving the `prod` security settings. `prod` alone opens zero-balance accounts; see [simulation mode](simulation-mode.md). Do not use `test` for operational health verification: `application-test.yml` deliberately maps DOWN to HTTP 200. The smoke check also examines JSON status, so that override cannot turn DOWN into a pass.

The static UI uses a cookie session; [browser session contract](browser-session.md) documents `Secure`/`__Host-` requirements, CSRF and logout behavior. Verify the session flow through the actual TLS ingress before release. The read-only smoke check does not exercise login or logout.

V27 creates a partial index for the HTTP PENDING backlog metric. It uses an ordinary `CREATE INDEX` inside the Flyway migration; on an already large `idempotency_keys` table, assess build time and write-lock impact in staging before rollout. A prebuilt, verified index or a planned maintenance window may be needed for that deployment. Do not infer a safe migration duration from the empty Testcontainers database.

## First response to a failing check

1. Record release digest, active profiles, failing endpoint/status, time, affected replicas and correlation IDs. Avoid collecting bearer tokens or full request bodies.
2. If global health fails but liveness succeeds, inspect PostgreSQL connectivity, Hikari waiters, Redis connectivity and application errors before restarting replicas. Authenticated health details may identify the failing component; keep those details within operator access.
3. If only readiness fails, inspect startup/migration logs and readiness state. If a probe path returns 404, check probe enablement and request routing. Do not replace a failing readiness check with a static successful endpoint.
4. If the anonymous accounts request returns 200, stop exposing that release and inspect the security whitelist/filter chain. This script checks one protected endpoint, not the complete authorization policy.
5. If a transfer result is uncertain after timeout/disconnect, preserve its original idempotency key and reconcile the stored outcome. Do not replay the same business operation with a fresh key or delete PENDING rows to clear an incident.

## Monitoring installation and acceptance

The repository provides instrumented counters and JSON logs, not a running alerting service. Before enabling traffic, install a scraper/alert destination, validate its authentication, and demonstrate that a synthetic alert reaches the responsible operator. Record actual Prometheus series/labels from the deployed build instead of assuming registry name conversion.

| Signal in the current implementation | Operator action / remaining work |
|---|---|
| `outbox.event.processed`, `outbox.event.failed`, `outbox.event.dead_letter` | These outcome counters now increment only after their outbox state transaction commits. Investigate new failures and dead letters; a healthy HTTP endpoint does not prove delivery. Use the [outbox runbook](outbox-operations.md). Persisted rows remain the recovery source; counters alone are not a delivery ledger. |
| `db.orphan.current`, `db.orphan.alarm`, `db.orphan.last_success_epoch_seconds` | Investigate any orphan. The last-success gauge is zero until all three queries finish, then records Unix seconds. Maintenance scans are single-flight across replicas via PostgreSQL advisory locks (`AdvisorySchedulerLock`), so only the leader's gauges move — aggregate staleness alerts with `max()` across instances (see `k8s/prometheus-rules.yaml`). With V24 foreign keys in force, a nonzero orphan count signals broken enforcement or corrupted/restored data, not normal application behavior. |
| HTTP response status/latency and DB pool/lock waits | Capture a staging baseline, then choose alert thresholds and minimum traffic windows from the service SLO. A status-only health check cannot establish latency or capacity. |
| `outbox.pending.current`, `outbox.oldest_pending.age_seconds`, `idempotency.http.pending.current`, `idempotency.http.oldest_pending.age_seconds`, `backlog.last_success_epoch_seconds` | A read-only scan runs every 60 seconds by default, single-flight across replicas via advisory locks (only the leader scans). The HTTP gauges exclude retained `outbox_handler_` dedup records (discriminated by `key_kind` since V38). Empty backlogs report zero; a zero last-success value means no scan has completed. Alert only after choosing a policy from staging traffic and scan cadence; investigate stalled items without automatic deletion. Since V46 all timestamp columns are TIMESTAMPTZ, age math is zone-safe. |
| `transfer.pending.reaped`, `transfer.pending.reap-conflicts` | Crash-window reaper activity (R-1, every 5 min by default). Sustained reaping means placements keep crashing between the PENDING save and completion — investigate pod restarts/OOMKills, not the reaper. Rising conflicts mean the reaper threshold races live traffic: raise `TRANSFER_REAPER_OLDER_THAN`. |
| `audit.default_partition.rows` | Partition-drift gauge from the 60s scan. Nonzero rows mean timestamps fell outside pre-created ranges — run `scripts/ensure_audit_partition.sql`. Pre-cutover the drift gauge reads 0. |
| `ledger.nonzero_transaction_refs` | Money-invariant gauge published by the nightly `LedgerReconciliationJob` (PERF-1: deliberately NOT part of the 60s scan — the full-table GROUP BY on append-only `ledger_entries` degrades linearly). Any nonzero value pages immediately. |
| Correlation IDs in JSON logs | Search request-local logs. Distributed tracing is wired (Micrometer + OTLP) but disabled by default; see [tracing](tracing.md) for collector setup. Until then, trace-looking IDs do not establish a cross-service trace. |

## Shutdown and rolling deployment drill

Use a staging release with PostgreSQL/Redis and a known set of synthetic accounts. Record the initial per-currency account totals, transfer IDs, idempotency keys, audit entries and pending outbox IDs. Generate bounded authenticated transfer traffic with stable keys, then terminate one application instance through the deployment platform while requests are in flight. The Docker entry point now uses `exec java`, allowing the JVM to receive SIGTERM.

Capture shutdown logs, readiness removal time, request outcomes, container exit reason and elapsed drain time. The application has a 30-second shutdown-phase limit and a 30-second use-case transaction timeout; outbox executor shutdown waits up to five seconds. The Kubernetes template has a startup probe, a preStop sleep for endpoint drain and 60-second termination grace. Validate that the platform removes traffic and finishes context shutdown within that measured budget; configured timeouts alone do not establish it.

After the replacement instance is ready, rerun the smoke check against each replica as well as the service route. Replay uncertain requests with their original keys and compare persisted transfer/account/audit outcomes. Success requires no unexplained balance change, duplicate successful operation or missing required audit record; pending outbox entries must resume or reach an investigated dead letter. Record failures instead of inferring success from an exit code. Partition-count changes need the separate quiesce/drain procedure in the [outbox runbook](outbox-operations.md); they are not ordinary rolling changes.

Rollback is not an automatic database downgrade. Before deployment, rehearse the preceding application version against the migrated staging schema, document compatibility, and choose a forward repair when old binaries cannot safely run. Preserve the image digest, schema history and relevant logs with the drill result.

## Backup and restore drill

No PostgreSQL backup/PITR configuration, Redis persistence/HA policy or proven recovery objective is provided by these templates. A named Compose database volume is not a backup. Assign owners and concrete RPO/RTO targets before calling the system recoverable.

1. In isolated staging, create a consistent baseline containing synthetic users, accounts, transfers, audit records, HTTP idempotency state and outbox pending/processed state. Record row counts and balances per currency, plus known completed and uncertain operation IDs.
2. Use the database platform's supported backup method with a separately protected destination. Record the backup timestamp, schema/Flyway version, image digest and restore prerequisites. Verify that the backup can be read; do not replace the active database during this drill.
3. Restore into a new isolated database/environment with outbound providers disabled or stubbed. Point a compatible application version at the restored database and verify migrations and constraints. Reconcile the recorded entities and operation outcomes; aggregate account totals alone cannot detect every wrong transfer.
4. Restore/test the security backend under its separately approved recovery policy. PostgreSQL recovery alone does not establish token-revocation durability; do not treat an empty Redis store as evidence that old bearer tokens are valid.
5. Run the HTTP smoke check, inspect dead letters/backlog and replay only designated synthetic uncertain requests with original keys. Compare record-level outcomes and record achieved data-loss interval and time to recovery against RPO/RTO.

Save the commands actually used, anonymized assertions, measured times and failed checks with the release record. Until this drill and alert/rolling-deployment acceptance have been performed, F20 remains partially remediated.

## Error responses (ProblemDetail map)

All failures render as RFC 7807 `application/problem+json` via
`ProblemDetailFactory`. Domain failures map through `BusinessErrorHttpMapper`
(exhaustiveness enforced at class-load: every `BusinessFailureKind` and every
`ErrorCode` has an entry); persistence/security/request failures have their
own handlers. Operator cheat-sheet:

| Failure kind (`BusinessFailureKind`) | HTTP | Typical `ErrorCode` |
|---|---|---|
| `RULE_VIOLATION` | 400 | `VALIDATION_FAILED`, `INVALID_ARGUMENT`, `INVALID_FORMAT`, `INVALID_ENUM_VALUE` |
| `NOT_FOUND` | 404 | `RESOURCE_NOT_FOUND` (also used for other-owned IDs/IBANs: no IDOR oracle) |
| `CONFLICT` | 409 | `OPTIMISTIC_LOCK_CONFLICT`, `UNIQUE_CONSTRAINT_VIOLATION`, `DB_INTEGRITY_VIOLATION`, `CONCURRENT_REQUEST` |
| `AUTHENTICATION_FAILED` | 401 | `AUTHENTICATION_FAILED` |
| `ACCESS_DENIED` | 403 | `ACCESS_DENIED` |
| `RATE_LIMITED` | 429 (+ `Retry-After`) | `RATE_LIMIT_EXCEEDED` |

Infrastructure signals outside the domain map: revocation-store outage →
503 `SECURITY_BACKEND_UNAVAILABLE` (fail-closed); unknown throwables → 500
`GENERAL_INTERNAL_ERROR` (logged with correlation ID, body carries no stack).
Unique-violation detection keys on SQLState `23505` down the cause chain.
