# Service-Level Objectives

Error budget policy: ship features while all SLOs hold; freeze non-urgent
releases when any budget burns faster than the 30-day allowance. Latency and
availability thresholds below are starting points — re-derive them from the
first month of staging traffic before attaching paging. Recovery objectives
(RPO ≤ 15 min, RTO ≤ 1 h) are ratified in `docs/disaster-recovery.md` and
measured by the quarterly drill (`ops/restore-drill.sh`), not re-derived here.

| SLI | Target (30d) | Signal |
|---|---|---|
| Transfer placement success | 99.9% of `POST /api/v1/transfers` → 2xx (excluding 4xx caller errors) | `http_server_requests_seconds_count{uri="/api/v1/transfers"}` |
| Transfer placement latency | p99 < 2s | `http_server_requests_seconds` histogram, same selector |
| No lost money movements | 100% — zero unexplained balance changes per reconciliation | Continuous gauge `ledger_nonzero_transaction_refs` (alert: `LedgerImbalance`, page on any nonzero group); the nightly manual check (V29 query) remains the drill procedure |
| Outbox delivery | 100% — zero dead letters | `outbox_event_dead_letter_total` (alert: `OutboxDeadLetter`) |
| Audit completeness | 100% — every transfer has legs + `TRANSFER_EXECUTED` row | `audit_event_consumed_total` tracks persisted rows (alert: `AuditDispatchStalled`) |
| Auth availability | 99.9% of login attempts answered (2xx/4xx, not 5xx) | `http_server_requests_seconds_count{uri="/api/v1/auth/login"}` |

Out of scope for SLOs (no paging): report/history latency (best-effort reads),
`429` rate-limit responses (client behavior, not service health).

## Security propagation expectations (F-15 — contract, not metric)

- Role/suspension revocation takes effect on admin paths and refresh
  immediately (DB-checked), but a live non-admin access token stays valid
  until expiry (15 min default, `JWT_ACCESS_EXPIRATION`). Worst case: a
  demoted user retains non-admin access for up to 15 minutes.
- This is an accepted trade-off (stateless verification, see
  `docs/decisions/token-version.md` G-2), recorded here so support and
  auditors share one expectation: "revocation within 15 minutes on data
  paths, immediate on admin paths and next refresh". Tighten only by
  lowering `JWT_ACCESS_EXPIRATION` (more refresh traffic) — not by
  re-checking the DB per request.

## Measurement evidence (long term #5 — added in this round)

- Grafana: `k8s/grafana-dashboard.json` (p50/p95/p99 + backlog + ledger gauge).
- Recording rule: every release attaches `ops/health_smoke.py --json` plus the
  k6 rate-limit log to the release tag; p95/p99 are read from these logs (the
  2s p99 above is a starting point, re-derived from the first month of staging
  traffic).
- Capacity model: 6 pods × 20 pool = 120 PG connections; requires managed PG
  ≥200 or PgBouncer (`k8s/bank-app.yaml:94-100`, `docs/operations.md`).
  Post-parallelization outbox ceiling is ~100-200 ev/s (partitions × workers);
  beyond that, the `docs/decisions/kafka-readiness.md` trigger applies.
- Reconciliation: `LedgerReconciliationJob` (03:30 cron) + `ledger.nonzero`
  counter; nonzero pages (critical), drill procedure in
  `docs/disaster-recovery.md`.
