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
    TOKEN_REVOKED,
    // PENDING -> FAILED reconciliation outcome, reserved for the documented
    // async placement path. No production caller today; the value and DB enum
    // entry (V50) stay for compatibility with the persisted audit trail.
    TRANSFER_MARKED_FAILED,
    // MFA lifecycle + password reset (V54/V55): enrollment, removal and
    // reset-request/completion are security-sensitive and audited. DB values
    // added in V55.
    MFA_ENABLED,
    MFA_DISABLED,
    // R-U08: a session-authenticated MFA management endpoint received a
    // wrong password step-up. DB value added in V58.
    MFA_REAUTH_FAILED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED;

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
