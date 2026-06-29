package com.bank.app.common.domain.exception;

public enum ErrorCode {
    GENERAL_INTERNAL_ERROR(500),
    VALIDATION_FAILED(400),
    INVALID_ARGUMENT(400),
    INVALID_FORMAT(400),
    INVALID_ENUM_VALUE(400),
    AUTHENTICATION_FAILED(401),
    ACCESS_DENIED(403),
    RESOURCE_NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    OPTIMISTIC_LOCK_CONFLICT(409),
    UNIQUE_CONSTRAINT_VIOLATION(409),
    DB_INTEGRITY_VIOLATION(409),
    CONCURRENT_REQUEST(409),
    UNSUPPORTED_MEDIA_TYPE(415);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String code() {
        return name();
    }
}
