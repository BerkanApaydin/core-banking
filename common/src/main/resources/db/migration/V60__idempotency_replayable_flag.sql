-- Owner: infra
-- Reason: never replay a truncated (corrupt) JSON body for an oversized response (R-I06)
--
-- V60__idempotency_replayable_flag.sql
--
-- Before this migration an oversized 2xx response was cut at 10 000 chars and
-- the fragment was replayed as application/json on a retry: likely invalid
-- JSON, and always a different body from the first response. Store whether the
-- body is replayable instead. Oversized responses keep a NULL body and
-- replayable = FALSE, so the replay path can return an explicit problem
-- response rather than corrupt JSON.
--
-- Existing completed rows are handled conservatively: the old truncation
-- always produced a body of exactly 10 000 chars. A legitimate body can also
-- be exactly 10 000 chars, but flagging it non-replayable only degrades that
-- retry to an explicit 500; replaying a truncated fragment would silently
-- return corrupt data. New completions are accurate because the application
-- writes the flag explicitly (an exact-cap body stays replayable).
--
-- The column is boolean with a server-side default, so the ALTER is metadata
-- only on PostgreSQL 11+ and does not rewrite the table.

ALTER TABLE idempotency_keys
    ADD COLUMN response_replayable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE idempotency_keys
SET response_replayable = FALSE
WHERE status = 'COMPLETED'
  AND response_body IS NOT NULL
  AND length(response_body) = 10000;
