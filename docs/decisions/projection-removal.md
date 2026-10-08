# YAGNI: Unused Daily Projection Removed

**Decision:** `transfer_daily_totals` (V44, rebuilt V46) is dropped (V48),
together with its refresh pipeline (`TransferReportProjectionRefresher`,
`TransferProjectionRefreshAdapter`, `RefreshTransferProjectionPort`).

**Why:** No read path ever queried the view — totals and reports run directly
against `transfers` via the V28 per-side indexes, V43 covering indexes and
UNION ALL aggregates. The nightly `REFRESH ... CONCURRENTLY` only cost write
amplification and scheduler noise while creating a false "projection exists"
impression (a future reader could have served stale data without a freshness
guard).

**Reintroduction rule:** If per-day rollups are ever needed, bring the view
back *together with* a freshness-guarded reader (max-age check + age alert).
A refresh job without a reader is not allowed back.
