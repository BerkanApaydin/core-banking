package com.bank.app.common.domain.exception;

/**
 * Meaning of an application failure, independent of the delivery protocol.
 * Adapters translate these categories to their own response or retry policy.
 */
public enum BusinessFailureKind {
    RULE_VIOLATION,
    NOT_FOUND,
    CONFLICT,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,
    RATE_LIMITED
}
