CREATE SEQUENCE ledger_entry_ids INCREMENT BY 50;
CREATE SEQUENCE audit_log_ids INCREMENT BY 50;

SELECT setval('ledger_entry_ids', COALESCE((SELECT MAX(id) FROM ledger_entries), 0) + 1, false);
SELECT setval('audit_log_ids', COALESCE((SELECT MAX(id) FROM audit_logs), 0) + 1, false);
