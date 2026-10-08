# Decision Index (docs/decisions/INDEX.md)

Short IDs like `K7/D8`, `S6`, `D14`, `K5/D15`, `K12/D5` appear in code. This file
is the single map: ID → file → one-sentence decision.

| ID | File | Decision (one sentence) |
|---|---|---|
| K1 | `application.yml:44`, `k8s/bank-app.yaml:83-93`, `docs/operations.md` | Single-ingress topology uses `PROXY_TRUST_HEADERS=true`; turn it off behind a CDN/multi-hop chain. |
| K3 | `application.yml:44`, `k8s/bank-app.yaml:94-100` | Connection budget: 6 pods × 20 = 120 backend connections; use managed PG ≥200 or PgBouncer. |
| K5/D15 | `IdempotencyAspect`, `IdempotentRetryExecutor` | Claim → decide → execute split; single backoff policy. |
| K7/D8 | `JwtAuthenticationFilter:173` | CSRF token is HMAC-bound to the server-verified user id. |
| K9/D7 | `k8s/bank-app.yaml:47-52` | Image `:latest` forbidden; digest pin (`pin-image-digest.sh` + CI guard). |
| K12/D5 | Schedulers (`IdempotencyCleanup`, `OutboxRetention`, `AuditRetention`, `BacklogMetrics`) | Advisory-lock single-flight; no tx, the lock guard owns the transaction. |
| S6 | `AdjustAccountBalancesUseCaseImpl:107` | Audit row travels as one object (no loose id+balance primitives). |
| D13/K14 | `JwtTokenProvider` | Typed properties; no `@Value` scatter. |
| D14/S12 | `AccountApiAdapter` | Published language is implemented in `adapter.out`. |
| D22 | `application.yml`, compose | Tracing off by default in prod (no pointless localhost dial). |
| G-1 | `docs/decisions/authorization-boundary.md` | Admin: URL boundary (`hasRole`) + use-case `hasRole`, two layers. |
| G-2 | `docs/decisions/token-version.md` | Access stateless (documented 15-min window); refresh DB-checked. |
| DB-1 | `docs/decisions/enum-types.md` | Native enum decision + value-add procedure; VARCHAR+CHECK roadmap for status. |
| V32 | `docs/release.md` (lock measurement) | Staging lock measurement mandatory before `ALTER TYPE` on large tables. |
| T-12 | `docs/decisions/partitioning.md` | Audit/ledger monthly partitioning roadmap (V45 preparation). |
| R-1 | `docs/decisions/pending-reaper.md` | Crash-window reaper marks stale PENDING transfers FAILED via the domain; no distributed lock, version conflicts counted. |

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
