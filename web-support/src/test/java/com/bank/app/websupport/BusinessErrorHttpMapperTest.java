package com.bank.app.websupport;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.domain.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BusinessErrorHttpMapper")
class BusinessErrorHttpMapperTest {

    @ParameterizedTest(name = "{0} preserves HTTP {1}")
    @CsvSource({
            "GENERAL_INTERNAL_ERROR, 500",
            "SECURITY_BACKEND_UNAVAILABLE, 503",
            "VALIDATION_FAILED, 400",
            "INVALID_ARGUMENT, 400",
            "INVALID_FORMAT, 400",
            "INVALID_ENUM_VALUE, 400",
            "AUTHENTICATION_FAILED, 401",
            "ACCESS_DENIED, 403",
            "RESOURCE_NOT_FOUND, 404",
            "METHOD_NOT_ALLOWED, 405",
            "OPTIMISTIC_LOCK_CONFLICT, 409",
            "UNIQUE_CONSTRAINT_VIOLATION, 409",
            "DB_INTEGRITY_VIOLATION, 409",
            "CONCURRENT_REQUEST, 409",
            "RATE_LIMIT_EXCEEDED, 429",
            "UNSUPPORTED_MEDIA_TYPE, 415",
            "API_VERSION_MISMATCH, 400"
    })
    void shouldMapEveryErrorCode(ErrorCode code, int expectedStatus) {
        assertThat(BusinessErrorHttpMapper.toStatus(code).value()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("should map every business failure kind")
    void shouldMapEveryBusinessFailureKind() {
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(BusinessFailureKind.RULE_VIOLATION)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(BusinessFailureKind.NOT_FOUND)))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(BusinessFailureKind.CONFLICT)))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(BusinessFailureKind.AUTHENTICATION_FAILED)))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(BusinessFailureKind.ACCESS_DENIED)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(BusinessFailureKind.RATE_LIMITED)))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("should default an exception with no failure kind to 400")
    void shouldDefaultNullFailureKindToBadRequest() {
        assertThat(BusinessErrorHttpMapper.toStatus(exceptionWithKind(null)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject null dispatch inputs")
    void shouldRejectNullInputs() {
        assertThatThrownBy(() -> BusinessErrorHttpMapper.toStatus((BusinessException) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BusinessErrorHttpMapper.toStatus((ErrorCode) null))
                .isInstanceOf(NullPointerException.class);
    }

    private static BusinessException exceptionWithKind(BusinessFailureKind kind) {
        return new BusinessException("error.test", null, "test") {
            @Override
            public BusinessFailureKind getFailureKind() {
                return kind;
            }
        };
    }
}
