-- V56__mfa_totp_replay_and_index_cleanup.sql
--
-- Owner: user / shared
-- Reason: (1) MFA brute-force + TOTP-replay hardening, (2) write
-- amplification on the hottest write table, (3) DB-level integrity for the
-- idempotency state machine.
--
-- 1) users.mfa_last_totp_counter holds the TOTP step counter of the last
--    accepted code. A TOTP code is valid for 2*skew+1 steps (90s with the
--    default skew of 1); without this column a captured code could be
--    replayed inside that window. The counter is advanced with a conditional
--    UPDATE (only a strictly newer counter is accepted), so two concurrent
--    replays of the same code cannot both win: the database, not the
--    application, is the arbiter.
--
-- 2) Dead transfers indexes. The V2/V3 indexes are strict prefixes of the
--    V43 per-side composites
--      idx_transfers_sender_created_id   (sender_account_id, created_at DESC, id DESC)
--      idx_transfers_receiver_created_id (receiver_account_id, created_at DESC, id DESC)
--    which serve every live transfer query, so the prefixes are provably
--    redundant (a btree can serve any prefix of its key) and cost write
--    amplification on every insert/update of the hottest write table.
--    idx_transfers_status_created and idx_transfers_created_at_desc are NOT
--    dropped here: the V49 protocol reserves that for pg_stat_user_indexes
--    evidence from staging, and V52 explicitly kept the DESC variant.
--
-- 3) idx_accounts_user_id_created_at lacked the id tiebreaker the query
--    ordering requires (ORDER BY created_at DESC, id DESC). Every other
--    ordering index in the schema (V35/V40/V43) ends with id DESC. The new
--    index is created BEFORE the old one is dropped so the account list path
--    is never without coverage.
--
-- 4) idempotency_keys.status had no CHECK constraint: the PENDING/COMPLETED/
--    FAILED machine was Java-only, so an out-of-band or corrupt row could
--    silently break the idempotency contract (the guard would throw
--    "Unknown idempotency status" at runtime).
--
-- HIGH-4: index builds and constraint validation on hot tables exceed the
-- 30s per-role statement_timeout on a populated DB. Lift it for this
-- migration only. For very large tables prefer the CONCURRENTLY operator
-- script outside Flyway's transaction (scripts/create_index_concurrently.sql).
SET LOCAL statement_timeout = '10min';

ALTER TABLE users ADD COLUMN mfa_last_totp_counter BIGINT;

DROP INDEX IF EXISTS idx_transfers_sender_account_id;
DROP INDEX IF EXISTS idx_transfers_receiver_account_id;
DROP INDEX IF EXISTS idx_transfers_sender_created;
DROP INDEX IF EXISTS idx_transfers_receiver_created;

CREATE INDEX IF NOT EXISTS idx_accounts_user_id_created_id
    ON accounts (user_id, created_at DESC, id DESC);

DROP INDEX IF EXISTS idx_accounts_user_id_created_at;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT chk_idempotency_key_status
    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));
