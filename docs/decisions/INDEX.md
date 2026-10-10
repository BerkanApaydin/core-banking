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
| K12/D5 | Schedulers (`IdempotencyCleanup`, `OutboxRetention`, `AuditRetention`, `BacklogMetrics`) | Advisory-lock single-flight; no tx, the lock guard owns the transaction. |
| S6 | `AdjustAccountBalancesUseCaseImpl.auditMovement` | Audit row travels as one object (no loose id+balance primitives). |
| D13/K14 | `JwtTokenProvider` | Typed properties; no `@Value` scatter. |
| D14/S12 | `AccountApiAdapter` | Published language is implemented in `adapter.out`. |
| D22 | `application.yml`, compose | Tracing off by default in prod (no pointless localhost dial). |
| G-1 | `docs/decisions/authorization-boundary.md` | Admin: URL boundary (`hasRole`) + use-case `hasRole`, two layers. |
| G-2 | `docs/decisions/token-version.md` | Access stateless on non-admin paths (documented 15-min window); admin paths + refresh DB-checked (SEC-01). |
| DB-1 | `docs/decisions/enum-types.md` | Native enum decision + value-add procedure; VARCHAR+CHECK roadmap for status. |
| V32 | `docs/release.md` (lock measurement) | Staging lock measurement mandatory before `ALTER TYPE` on large tables. |
| T-12 | `docs/decisions/partitioning.md` | Audit/ledger monthly partitioning roadmap (V45 preparation). |
| R-1 | `docs/decisions/pending-reaper.md` | Crash-window reaper marks stale PENDING transfers FAILED via the domain; no distributed lock, version conflicts counted. |
| T-13 | `docs/decisions/account-locking.md` | Balance mutation keeps ordered pessimistic read-lock + optimistic versioned write; each side has a defined job, neither may be dropped alone. |
| K19 | `docs/decisions/api-versioning.md` | URL versioning via `@ApiVersion` + additive-change rules; published language evolves by addition only. |
| — | `docs/decisions/audit-atomicity.md` | Three audit write modes: mandatory-same-tx (money), best-effort-auth (login/logout), observe-after-commit (dispatch). |
| K17 | `docs/decisions/cross-bc-read-model.md` | Transfer reads account via published language + snapshot cache; projection direction when that stops being enough. |
| — | `docs/decisions/dependency-review.md` | OSV gate on the SBOM + accepted risks in the allowlist, re-reviewed on Boot upgrades. |
| — | `docs/decisions/infrastructure-split.md` | Roadmap: split `infrastructure` into web/security/outbox/observability modules. |
| K18 | `docs/decisions/saga-readiness.md` | Documented (not implemented) saga path; tripwire comments at the ACL calls. |
| — | `docs/decisions/test-placement.md` | Spring-context tests live in `app`; unit tests live in their owning modules. |
| — | `docs/decisions/transaction-strategy.md` | Marker-aspect programmatic tx for use cases; declarative `@Transactional` for single-boundary adapters. |
| S10/D18 | `docs/decisions/transfer-failed-state.md` | FAILED state retained (reachable via the pending reaper); revisit if async placement stays absent. |

## Newly added decisions (this improvement round)

- `authorization-boundary.md` (G-1 two layers + ArchUnit rule)
- `token-version.md` (G-2 accepted trade-off + strict-mode flag)
- `enum-types.md` (DB-1: native enum procedure + rollback plan)
- `partitioning.md` (T-12: maintenance-window procedure)
- `retry-policy.md` (T-6 unified policy: ExponentialBackoffPolicy + Sleeper)
- `keyset-pagination.md` (DB-2: cursor contract)
- `kafka-readiness.md` (long term: OutboxPort → broker migration)
- `time-strategy.md` (UTC-only timestamps: no bare now()/systemDefaultZone in main)
- `pending-reaper.md` (R-1: stale-PENDING crash-window recovery through the domain)
- `projection-removal.md` (YAGNI: unused `transfer_daily_totals` dropped with its refresh pipeline, V48)
- `arch-test-hardening.md` (ArchUnit round: invocation-not-wiring auth checks, 4-way adapter symmetry, adapter→infra ban, `Transfer.create` gate, infra→account guard; `user-api`/`audit-api` extraction deliberately deferred)
| O-1 | `docs/decisions/secret-management.md` (2026-10 rotation) | Leaked dev-default JWT secret rotated; history still counts as compromised. |
| O-3 | `docs/decisions/token-version.md` (2026-10 acceptance) | 15-min window accepted on money paths too; no per-request tokenVersion lookup in transfer. |
| O-12 | `docs/decisions/iban-pii.md` | Raw IBANs masked at exception construction via `IbanLogMask`; never at the log site. |
| TX-P | `docs/decisions/transaction-strategy.md` (boundary port) | Adapters observe tx state via `TransactionBoundaryPort`; no programmatic Spring TX in BCs (ArchUnit). |
| CACHE-1 | `docs/decisions/cache-prefix-alias.md` | Canonical `app.cache.account-info.*` keys; legacy prefix deprecated fallback with canonical-wins merge. |
| AUD-1 | `docs/decisions/audit-persistence.md` | `AuditLogJpaEntity` intentionally outside the shared auditing base; explicit `@PrePersist` only. |
