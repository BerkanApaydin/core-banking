-- HIGH-4: table rewrite exceeds the 30s per-role statement_timeout on a
-- populated DB. Lift it for this migration only.
SET LOCAL statement_timeout = '10min';

ALTER TABLE accounts ALTER COLUMN active TYPE VARCHAR(20)
    USING CASE WHEN active THEN 'ACTIVE' ELSE 'SUSPENDED' END;
ALTER TABLE accounts ALTER COLUMN active SET NOT NULL;
ALTER TABLE accounts RENAME COLUMN active TO status;
