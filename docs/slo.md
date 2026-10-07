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
