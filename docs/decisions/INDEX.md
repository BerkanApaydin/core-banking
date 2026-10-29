# Decision Index (docs/decisions/INDEX.md)

Short IDs like `K7/D8`, `S6`, `D14`, `K5/D15`, `K12/D5` appear in code. This file
is the single map: ID → file → one-sentence decision.

| ID | File | Decision (one sentence) |
|---|---|---|
| K1 | `application.yml` (`proxy.trust-forwarded-headers`), `k8s/bank-app.yaml` (`PROXY_TRUST_HEADERS`), `docs/operations.md` | Single-ingress topology uses `PROXY_TRUST_HEADERS=true`; turn it off behind a CDN/multi-hop chain. |
| K3 | `application.yml` (Hikari `maximum-pool-size`), `k8s/bank-app.yaml` (`DB_MAX_POOL_SIZE`) | Connection budget: 6 pods × 20 = 120 backend connections; use managed PG ≥200 or PgBouncer. |
| K5/D15 | `IdempotencyAspect`, `IdempotentRetryExecutor` | Claim → decide → execute split; single backoff policy. |
| K7/D8 | `JwtAuthenticationFilter` (bound-CSRF comment) | CSRF token is HMAC-bound to the server-verified user id. |
| K9/D7 | `k8s/bank-app.yaml` (`image:` pin block) | Image `:latest` forbidden; digest pin (`pin-image-digest.sh` + CI guard). |
| K12/D5 | Schedulers (`IdempotencyCleanupScheduler`, `OutboxRetentionScheduler`, `AuditRetentionScheduler`, `BacklogMetricsReporter`) | Advisory-lock single-flight; no tx, the lock guard owns the transaction. |
| S6 | `AdjustAccountBalancesUseCaseImpl.auditMovement` | Audit row travels as one object (no loose id+balance primitives). |
| D13/K14 | `JwtTokenProvider` | Typed properties; no `@Value` scatter. |
| D14/S12 | `AccountApiAdapter` | Published language is implemented by the provider as an inbound adapter (`adapter.in.api`); the consumer's ACL stays outbound. |
| D22 | `application.yml`, compose | Tracing off by default in prod (no pointless localhost dial). |
| G-1 | `docs/decisions/authorization-boundary.md` | Admin: URL boundary (`hasRole`) + use-case `hasRole`, two layers. |
| G-2 | `docs/decisions/token-version.md` | Access stateless on non-admin paths (documented 15-min window); admin paths + refresh DB-checked (SEC-01). |
| DB-1 | `docs/decisions/enum-types.md` | Native enum decision + value-add procedure; VARCHAR+CHECK roadmap for status. |
| V32 | `docs/release.md` (lock measurement) | Staging lock measurement mandatory before `ALTER TYPE` on large tables. |
| T-12 | `docs/decisions/partitioning.md` | Audit/ledger monthly partitioning roadmap (V45 preparation). |
| T-13 | `docs/decisions/account-locking.md` | Balance mutation keeps ordered pessimistic read-lock + optimistic versioned write; each side has a defined job, neither may be dropped alone. |
| K19 | `docs/decisions/api-versioning.md` | URL versioning via `@ApiVersion` + additive-change rules; published language evolves by addition only. |
| — | `docs/decisions/audit-atomicity.md` | Three audit write modes: mandatory-same-tx (money), best-effort-auth (login/logout/MFA/reset), observe-after-commit (dispatch). Logout uses `publishBestEffort` with a `REQUIRES_NEW` retry outbox (`AuditRetryOutboxRelay`); other auth flows still use plain best-effort. |
| K17 | `docs/decisions/cross-bc-read-model.md` | Transfer reads account via published language + snapshot cache; projection direction when that stops being enough. |
| — | `docs/decisions/dependency-review.md` | OSV gate on the SBOM + accepted risks in the allowlist, re-reviewed on Boot upgrades. |
| — | `docs/decisions/infrastructure-split.md` | Roadmap: split `infrastructure` into web/security/outbox/observability modules. |
| K18 | `docs/decisions/saga-readiness.md` | Documented (not implemented) saga path; tripwire comments at the ACL calls. |
| — | `docs/decisions/test-placement.md` | Spring-context tests live in `app`; unit tests live in their owning modules. |
| — | `docs/decisions/transaction-strategy.md` | Marker-aspect programmatic tx for use cases; declarative `@Transactional` for single-boundary adapters. |
| S10/D18 | `docs/decisions/transfer-failed-state.md` | FAILED state retained for the documented async placement path (no production caller today); relay stays wired for that future flow; revisit if async placement stays absent. |
| S11/D23 | `docs/decisions/mfa-totp.md` | TOTP MFA (per-user opt-in, AES-GCM secrets, fail-fast prod key) + enumeration-safe password reset; authenticated MFA failures are 400 (`MFA_CODE_INVALID`), login stays uniform 401. |
| MFA-1 (2026-10) | `docs/decisions/mfa-totp.md` | MFA code checks share one service: account-keyed attempt budget (429) enforced at every call site (the login path consults it after the password check), TOTP replay counter consumed by the login path only, `/api/v1/auth/mfa/*` in the tight rate-limit tier. |
| OUTBOX-1 (2026-10) | `docs/decisions/retry-policy.md`, V57 | A failed outbox event waits for an exponential backoff before the next attempt: `max-retries` is a budget, not a count. |
| REV-1 (2026-10) | `docs/token-revocation-migration.md` | Hybrid revocation: short-lived NEGATIVE cache for the durable lookup (the revocation check no longer competes with the money path for DB connections); the Redis lookup is one Lua round trip. |
| LOCK-1 (2026-10) | `application.yml` (`DB_CONNECTION_TIMEOUT_MS`) | The connection wait must not be shorter than the use-case transaction budget: pool saturation becomes a 500 burst otherwise. |
| MANIFEST-1 (2026-10) | `scripts/check_k8s_manifest_contract.py` | The Kubernetes manifest is compared against the app's prod preconditions in CI (a missing `CORS_ALLOWED_ORIGINS` deploys a CrashLoopBackOff). |
| I-09 | `docs/decisions/migration-ownership.md` | Focused follow-up to R-C07: Flyway resources live in the resources-only `migrations` module; owner headers and heavy-statement rules keep the future per-BC split safe. |
| STAT-1 (2026-10) | `docs/decisions/static-analysis.md` | SpotBugs runs in `verify` (effort Max, threshold Medium, reviewed exclusions); Checkstyle/PMD/ErrorProne stay unwired until their engines exist in the offline cache. |
| AUDIT-2 (2026-10) | `docs/decisions/audit-integrity.md` | V67 links audit rows with a DB-side SHA-256 chain, a nightly verifier + `audit.chain.broken` gauge, and prefix-only retention; the opt-in daily HMAC anchor exports the head outside the database. WORM/KMS custody and the owner/superuser boundary remain open. |
| LEAST-PRIV-1 (2026-10) | `docs/decisions/least-privilege-db-roles.md` | V68 + the provisioning script run the app as insert/select-only `bank_app`, add a separate `bank_scheduler` role with `SELECT` only on `ledger_entries`, split Flyway to the owner, guard retention with a 30-day floor, and flag an empty table with a non-zero chain head. |
| OBS-1 (2026-10) | `application.yml`, `k8s/prometheus-rules.yaml` | Ledger reconciliation runs 6-hourly by default, publishes a last-success epoch gauge, and tickets after 8h without a completed scan; PERF-1 keeps the full-table scan out of the 60s loop. |
| AUDIT-3 (2026-10) | `V69__audit_partition_aware_retention.sql`, `docs/decisions/partitioning.md` | Partitioned audit retention drops only fully expired, chain-prefix-safe partitions; a backdated high-`chain_seq` row blocks the drop and falls back to the V67 prefix DELETE. |

## Newly added decisions (this improvement round)

- `authorization-boundary.md` (G-1 two layers + ArchUnit rule)
- `token-version.md` (G-2 accepted trade-off + strict-mode flag)
- `enum-types.md` (DB-1: native enum procedure + rollback plan)
- `partitioning.md` (T-12: maintenance-window procedure)
- `retry-policy.md` (T-6 unified policy: ExponentialBackoffPolicy + Sleeper)
- `keyset-pagination.md` (DB-2: cursor contract)
- `kafka-readiness.md` (long term: OutboxPort → broker migration)
- `time-strategy.md` (UTC-only timestamps: no bare now()/systemDefaultZone in main)
- `shared-kernel-web-contracts.md` (R-C07: framework-free web contracts stay in `common`; a separate `web-support` module now hosts Spring-dependent RFC 7807 rendering, keeping the dependency arrow inward)
- `projection-removal.md` (YAGNI: unused `transfer_daily_totals` dropped with its refresh pipeline, V48)
- `arch-test-hardening.md` (ArchUnit round: invocation-not-wiring auth checks, 4-way adapter symmetry, adapter→infra ban, `Transfer.create` gate, infra→account guard; `user-api`/`audit-api` extraction deliberately deferred)
- `static-analysis.md` (STAT-1: SpotBugs verify gate + reviewed exclusions; extended Python structural rules)
- `least-privilege-db-roles.md` (LEAST-PRIV-1: runtime/owner split, append-only evidence grants, retention cutoff floor, empty-table chain signal)
- `audit-integrity.md` (AUDIT-2/AUDIT-3: hash chain + verifier, opt-in external HMAC anchor, prefix retention, partition-aware `DROP PARTITION` with backdated-row fallback)

## Operational decisions

| ID | File | Decision (one sentence) |
|---|---|---|
| O-1 | `docs/decisions/secret-management.md` (2026-10 rotation) | Leaked dev-default JWT secret rotated; history still counts as compromised. |
| JWT-1 (2026-10) | `docs/decisions/secret-management.md` (token identity claims) | Access/refresh tokens require the configured issuer/audience and carry a `kid` header; verification allows a bounded 30-second clock skew. |
| O-3 | `docs/decisions/token-version.md` (2026-10 acceptance) | 15-min window accepted on money paths too; no per-request tokenVersion lookup in transfer. |
| O-12 | `docs/decisions/iban-pii.md` | Raw IBANs masked at exception construction via `IbanLogMask`; never at the log site. |
| TX-P | `docs/decisions/transaction-strategy.md` (boundary port) | Adapters observe tx state via `TransactionBoundaryPort`; no programmatic Spring TX in BCs (ArchUnit). |
| CACHE-1 | `docs/decisions/cache-prefix-alias.md` | Canonical `app.cache.account-info.*` keys win on presence; shipped defaults live on the deprecated legacy prefix (alias) and the resolver falls back to it. |
| AUD-1 | `docs/decisions/audit-persistence.md` | `AuditLogJpaEntity` intentionally outside the shared auditing base; explicit `@PrePersist` only. |
| R-C03 | `application-dev.yml`, `DatasourceUtcContractTest` | Dev datasource repeats the base UTC session option; a contract test pins base/dev parity. |
| R-C04 | `UtcDateTimeProvider`, `JpaAuditingConfig` | JPA auditing uses a UTC `DateTimeProvider`; the clock zone cannot leak into `created_at`/`updated_at`. |
| R-C05 | `OrphanIntegrityProperties` | Orphan cron and threshold fail fast at binding instead of accepting silently invalid values. |
| R-C06 | `DemoSeedProperties`, `application-demo.yml` | Demo seed passwords come from config/env; the demo profile requires explicit values and refuses blanks. |
| R-C07 | `migrations/pom.xml`, `docs/decisions/shared-kernel-web-contracts.md` | Flyway resources live in a dedicated `migrations` module; framework-free web contracts stay in the shared kernel, while Spring-dependent rendering lives in `web-support`. |
