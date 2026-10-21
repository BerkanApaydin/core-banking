-- Owner: infra
-- Reason: widen idempotency response_body to TEXT so oversized replay bodies cannot roll back business tx (L6)
--
-- V53__widen_idempotency_response_body.sql
--
-- L6: idempotency_keys.response_body was VARCHAR(10000) while completion
-- joins the business transaction (IdempotencyGuard.completeRequest,
-- Propagation.REQUIRED). Any response body above 10 KB failed the completion
-- write and rolled the business mutation back with it. Current API bodies are
-- small, but the coupling is real if payloads grow.
-- Widen to unbounded TEXT (same as outbox payload, V14); the application cap
-- lives in IdempotencyPersistenceAdapter (truncate + warn, replay-only field)
-- so the database no longer enforces a business-transaction failure mode.
--
-- HIGH-4: ALTER COLUMN TYPE rewrites the table on a populated DB; lift the
-- 30s per-role statement_timeout for this migration only.
SET LOCAL statement_timeout = '10min';

ALTER TABLE idempotency_keys ALTER COLUMN response_body TYPE TEXT;
