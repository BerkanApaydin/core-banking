-- The monolith writes these aggregates in one PostgreSQL transaction. Keep
-- scalar IDs in Java while enforcing referential integrity in the database.
-- Validation deliberately fails deployment if older data contains orphans;
-- operators must repair those rows before retrying this migration.
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_user_id
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT NOT VALID;
ALTER TABLE transfers ADD CONSTRAINT fk_transfers_sender_id
    FOREIGN KEY (sender_account_id) REFERENCES accounts (id) ON DELETE RESTRICT NOT VALID;
ALTER TABLE transfers ADD CONSTRAINT fk_transfers_receiver_id
    FOREIGN KEY (receiver_account_id) REFERENCES accounts (id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE accounts VALIDATE CONSTRAINT fk_accounts_user_id;
ALTER TABLE transfers VALIDATE CONSTRAINT fk_transfers_sender_id;
ALTER TABLE transfers VALIDATE CONSTRAINT fk_transfers_receiver_id;
