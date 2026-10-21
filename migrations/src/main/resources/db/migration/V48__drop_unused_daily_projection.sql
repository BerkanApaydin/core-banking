-- V48__drop_unused_daily_projection.sql
--
-- YAGNI removal: transfer_daily_totals (V44, rebuilt in V46) never gained a
-- read path. The totals/report queries run directly against transfers via
-- per-side indexes (V28/V43) and UNION ALL aggregates; the nightly
-- CONCURRENTLY refresh only burned write amplification and scheduler noise.
-- The unique index drops with the view. If per-day rollups are ever needed,
-- reintroduce the view together with a freshness-guarded reader (never a
-- blind refresh again) — see docs/decisions/projection-removal.md.

DROP MATERIALIZED VIEW IF EXISTS transfer_daily_totals;
