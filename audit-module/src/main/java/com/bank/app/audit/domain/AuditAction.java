package com.bank.app.audit.domain;

public enum AuditAction {
    ACCOUNT_CREATED,
    ACCOUNT_DEBITED,
    ACCOUNT_CREDITED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_CLOSED,
    TRANSFER_EXECUTED,
    TRANSFER_CANCELLED;

    public static AuditAction fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Audit action must not be null");
        }
        return valueOf(value);
    }
}
