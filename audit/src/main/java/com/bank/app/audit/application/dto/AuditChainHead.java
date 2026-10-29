package com.bank.app.audit.application.dto;

/**
 * The database-side head of the audit hash chain. {@code lastHash} is the
 * empty string for a fresh chain (state last_seq = 0) and a 64-character
 * lowercase hex SHA-256 otherwise.
 */
public record AuditChainHead(long lastSeq, String lastHash) {

    public AuditChainHead {
        if (lastSeq < 0) {
            throw new IllegalArgumentException("Audit chain lastSeq must not be negative: " + lastSeq);
        }
        if (lastHash == null) {
            lastHash = "";
        }
        if (lastHash.isEmpty() && lastSeq != 0) {
            throw new IllegalArgumentException("A non-zero audit chain head must include lastHash");
        }
        if (!lastHash.isEmpty() && !lastHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Audit chain lastHash must be 64 lowercase hex characters");
        }
    }
}
