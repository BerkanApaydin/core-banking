package com.bank.app.audit.domain;

public enum AuditAction {
    ACCOUNT_CREATED,
    ACCOUNT_DEBITED,
    ACCOUNT_CREDITED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_CLOSED,
    TRANSFER_EXECUTED,
    TRANSFER_CANCELLED,
    // Authentication lifecycle (K11/D4, DB: V33). PASSWORD_CHANGED is reserved
    // for a future password-change flow; ACCOUNT_IBAN_VIEWED stays out
    // deliberately (read-path volume belongs in access logs).
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_CHANGED,
    TOKEN_REVOKED;

    public static AuditAction fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Audit action must not be null");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw new UnknownAuditActionException(value);
        }
    }
}
