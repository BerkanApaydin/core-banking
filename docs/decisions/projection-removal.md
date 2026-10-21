# YAGNI: Unused Daily Projection Removed

**Decision:** `transfer_daily_totals` (V44, rebuilt V46) is dropped (V48),
together with its refresh pipeline (`TransferReportProjectionRefresher`,
`TransferProjectionRefreshAdapter`, `RefreshTransferProjectionPort`).

**Why:** No read path ever queried the view — totals and reports run directly
against `transfers` via the V61 per-side business-time indexes, the V43
insert-order indexes and UNION ALL aggregates. The nightly `REFRESH ... CONCURRENTLY` only cost write
amplification and scheduler noise while creating a false "projection exists"
impression (a future reader could have served stale data without a freshness
guard).

**Reintroduction rule:** If per-day rollups are ever needed, bring the view
back *together with* a freshness-guarded reader (max-age check + age alert).
A refresh job without a reader is not allowed back.

**Migration churn note (R-O04):** V46 had to drop and recreate the view around
the `transfers.business_created_at` type change; V48 then removed it. That
recreate was one-time work per database and is historical — V46's checksum is
frozen, so it must not be rewritten. Future projection removals follow the
net-zero rule in
[migration ownership](migration-ownership.md): do not rebuild a projection in
an earlier migration and drop it in a later migration of the same release.
