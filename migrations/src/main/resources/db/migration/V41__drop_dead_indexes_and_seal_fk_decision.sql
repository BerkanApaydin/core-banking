-- V41: dead index cleanup + FK decision seal (monolith stays, FKs kept).
-- V19/V22 NO-FK superseded by V24 restore; OrphanIntegrityReporter stays as observer.
DROP INDEX IF EXISTS idx_accounts_user_id;
DROP INDEX IF EXISTS idx_accounts_iban_user;
DROP INDEX IF EXISTS idx_idempotency_cleanup_http;
DROP INDEX IF EXISTS idx_idempotency_cleanup_handler;
-- V27 prefix predicate index superseded by V38 kind-based partial indexes
DROP INDEX IF EXISTS idx_pending_http_idempotency;
-- Record: financial tables keep sequential BIGINT, outbox/idempotency keep the assigned-string rule.
