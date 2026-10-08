package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Owns HTTP policy while domain failures retain protocol-independent meaning.
 *
 * <p>Dispatch tables instead of switch statements: adding a new failure kind
 * or error code is a one-line table entry below, with no dispatch logic to
 * touch. The static guard fails fast at class-load time if any enum constant
 * is left unmapped — the exhaustiveness guarantee a switch would otherwise
 * give at compile time.
 */
public final class BusinessErrorHttpMapper {

    private BusinessErrorHttpMapper() {}

    private static final Map<BusinessFailureKind, HttpStatus> FAILURE_STATUSES = failureStatuses();
    private static final Map<ErrorCode, HttpStatus> CODE_STATUSES = codeStatuses();

    static {
        assertComplete(BusinessFailureKind.class, FAILURE_STATUSES);
        assertComplete(ErrorCode.class, CODE_STATUSES);
    }

    private static Map<BusinessFailureKind, HttpStatus> failureStatuses() {
        Map<BusinessFailureKind, HttpStatus> statuses = new EnumMap<>(BusinessFailureKind.class);
        statuses.put(BusinessFailureKind.RULE_VIOLATION, HttpStatus.BAD_REQUEST);
        statuses.put(BusinessFailureKind.NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(BusinessFailureKind.CONFLICT, HttpStatus.CONFLICT);
        statuses.put(BusinessFailureKind.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        statuses.put(BusinessFailureKind.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        statuses.put(BusinessFailureKind.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
        return statuses;
    }

    private static Map<ErrorCode, HttpStatus> codeStatuses() {
        Map<ErrorCode, HttpStatus> statuses = new EnumMap<>(ErrorCode.class);
        statuses.put(ErrorCode.GENERAL_INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        statuses.put(ErrorCode.SECURITY_BACKEND_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        statuses.put(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.INVALID_FORMAT, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.INVALID_ENUM_VALUE, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        statuses.put(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        statuses.put(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED);
        statuses.put(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.UNIQUE_CONSTRAINT_VIOLATION, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.DB_INTEGRITY_VIOLATION, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.CONCURRENT_REQUEST, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);
        statuses.put(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        // API-03: 400, not 406 — 406 is content negotiation; a version
        // mismatch is a malformed request (same code the version filter emits).
        statuses.put(ErrorCode.API_VERSION_MISMATCH, HttpStatus.BAD_REQUEST);
        return statuses;
    }

    private static <E extends Enum<E>> void assertComplete(Class<E> type, Map<E, HttpStatus> statuses) {
        for (E constant : type.getEnumConstants()) {
            if (!statuses.containsKey(constant)) {
                throw new IllegalStateException(
                        "BusinessErrorHttpMapper has no HTTP status for " + type.getSimpleName() + "." + constant);
            }
        }
    }

    public static HttpStatus toStatus(BusinessException exception) {
        BusinessFailureKind kind = Objects.requireNonNull(exception, "Exception must not be null").getFailureKind();
        if (kind == null) {
            return HttpStatus.BAD_REQUEST;
        }
        HttpStatus status = FAILURE_STATUSES.get(kind);
        if (status == null) {
            throw new IllegalStateException("No HTTP status mapped for failure kind: " + kind);
        }
        return status;
    }

    public static HttpStatus toStatus(ErrorCode code) {
        Objects.requireNonNull(code, "Error code must not be null");
        HttpStatus status = CODE_STATUSES.get(code);
        if (status == null) {
            throw new IllegalStateException("No HTTP status mapped for error code: " + code);
        }
        return status;
    }
}
