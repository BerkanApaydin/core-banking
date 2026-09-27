package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Owns HTTP policy while domain failures retain protocol-independent meaning. */
public final class BusinessErrorHttpMapper {

    private BusinessErrorHttpMapper() {}

    public static HttpStatus toStatus(BusinessException exception) {
        return switch (exception.getFailureKind()) {
            case RULE_VIOLATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case null -> HttpStatus.BAD_REQUEST;
        };
    }

    public static HttpStatus toStatus(ErrorCode code) {
        return switch (code) {
            case GENERAL_INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SECURITY_BACKEND_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case VALIDATION_FAILED, INVALID_ARGUMENT, INVALID_FORMAT, INVALID_ENUM_VALUE -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case OPTIMISTIC_LOCK_CONFLICT, UNIQUE_CONSTRAINT_VIOLATION,
                    DB_INTEGRITY_VIOLATION, CONCURRENT_REQUEST -> HttpStatus.CONFLICT;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        };
    }
}
