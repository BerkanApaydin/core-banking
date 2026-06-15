-- V3__integrity_and_improvements.sql
-- 1. Foreign Key Constraints
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_user_id FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE transfers ADD CONSTRAINT fk_transfers_sender_id FOREIGN KEY (sender_account_id) REFERENCES accounts (id);
ALTER TABLE transfers ADD CONSTRAINT fk_transfers_receiver_id FOREIGN KEY (receiver_account_id) REFERENCES accounts (id);

-- 2. Check Constraints
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_balance CHECK (balance >= 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_amount CHECK (amount > 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_no_self_transfer CHECK (sender_account_id <> receiver_account_id);
ALTER TABLE transfers ADD CONSTRAINT chk_transfers_status CHECK (status IN ('COMPLETED', 'CANCELLED'));

-- 3. Composite Indices
CREATE INDEX idx_transfers_sender_created ON transfers (sender_account_id, created_at DESC);
CREATE INDEX idx_transfers_receiver_created ON transfers (receiver_account_id, created_at DESC);

-- 4. Audit Logs Indices
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_logs_username ON audit_logs (username);

-- 5. Version column for transfers
ALTER TABLE transfers ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- 6. Idempotency Keys Table
CREATE TABLE idempotency_keys (
    key_value VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    response_body VARCHAR(10000),
    created_at TIMESTAMP(6) NOT NULL
);
