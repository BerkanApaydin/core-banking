-- Hibernate's pooled sequence optimizer reserves a block whose END is the
-- value returned by nextval. V63 seeded the new sequences at MAX(id) + 1, so
-- the first reserved block overlapped existing primary keys. Leave one full
-- allocation block of headroom so the first block starts above the current
-- maximum for both the pooled and pooled-lo optimizers.
SELECT setval('ledger_entry_ids', COALESCE((SELECT MAX(id) FROM ledger_entries), 0) + 50, false);
SELECT setval('audit_log_ids', COALESCE((SELECT MAX(id) FROM audit_logs), 0) + 50, false);
