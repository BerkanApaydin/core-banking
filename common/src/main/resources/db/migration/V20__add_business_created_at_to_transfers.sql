-- V20__add_business_created_at_to_transfers.sql
-- Separates business time from persistence auditing time on transfers.
--
-- Until now Transfer.createdAt (used by the 24-hour cancellation window) was
-- mapped to the auditing created_at column populated by Spring Data auditing at
-- insert time. After any save→reload round trip the domain timestamp silently
-- became the persistence timestamp, breaking time-travel determinism
-- (ClockProviderPort) and overloading one column with two meanings.
--
-- business_created_at stores the domain creation instant assigned in
-- Transfer.create(...). Backfilled from created_at for existing rows.
-- The mapper prefers business_created_at and falls back to created_at for
-- legacy rows. Ordering queries keep using created_at (insert order).

ALTER TABLE transfers ADD COLUMN business_created_at TIMESTAMP(6);

UPDATE transfers SET business_created_at = created_at WHERE business_created_at IS NULL;
