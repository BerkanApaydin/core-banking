-- V35__audit_actor_user_id_index.sql
--
-- First filtered query on V34's actor_user_id (LoadAuditLogPort.findByActor):
-- per-identity audit lookup for incident review ("what did user X do?").
-- V34 deliberately added the column without an index ("no query filters on
-- it"); this migration adds the index together with its query, not before.
--
-- Composite (actor_user_id, timestamp DESC, id DESC) matches the ORDER BY of
-- the repository method, so the per-actor listing is an index-only range scan
-- followed by no sort. NULL actor rows (history/system legs) are excluded by
-- the equality predicate and never touch this index. No FK by design (V34):
-- audit retention stays independent of the user lifecycle.

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_timestamp
    ON audit_logs (actor_user_id, timestamp DESC, id DESC);
