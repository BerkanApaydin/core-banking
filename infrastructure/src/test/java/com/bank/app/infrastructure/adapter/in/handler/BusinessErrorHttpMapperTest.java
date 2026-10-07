package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.accountapi.AccountNotFoundException;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.domain.exception.CurrencyMismatchException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
            "UNSUPPORTED_MEDIA_TYPE, 415"
    })
    void shouldPreserveErrorResponseContract(ErrorCode code, int expectedStatus) {
        var response = ProblemDetailFactory.create(code, "Public message", null);

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", code.code())
                .containsEntry("message", "Public message");
    }

    @ParameterizedTest(name = "{0} maps to HTTP {1} with code {2}")
    @MethodSource("businessFailures")
    void shouldPreserveConcreteBusinessFailureResponses(
            BusinessException exception, int expectedStatus, String expectedCode) throws Exception {
        var messages = new ProblemMessageResolver(new StaticMessageSource());
        // Deliberately fallback-first: @Order on the advice classes (not
        // registration order) must decide which handler wins.
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController(exception))
                .setControllerAdvice(new GlobalExceptionHandler(messages),
                        new BusinessProblemHandler(messages, null)).build();

        assertThat(BusinessErrorHttpMapper.toStatus(exception).value()).isEqualTo(expectedStatus);
        mvc.perform(get("/failure"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    static Stream<Arguments> businessFailures() {
        return Stream.of(
                Arguments.of(new CurrencyMismatchException("Different currencies"), 400, "CURRENCY_MISMATCH"),
                Arguments.of(new AccountNotFoundException(42L), 404, "ACCOUNT_NOT_FOUND_ID"),
                Arguments.of(new AccountNotFoundException("TR000"), 404, "ACCOUNT_NOT_FOUND_IBAN"),
                Arguments.of(new com.bank.app.account.domain.exception.AccountNotFoundException(42L),
                        404, "ACCOUNT_NOT_FOUND_ID"),
                Arguments.of(new UserNotFoundException("Unknown user"), 404, "USER_NOT_FOUND"),
                Arguments.of(new DuplicateIbanException("TR000"), 409, "DUPLICATE_IBAN"),
                Arguments.of(new ConcurrentRequestException("Already running"), 409, "CONCURRENT_REQUEST"),
                Arguments.of(new AuthenticationFailedException("Invalid credentials"), 401, "AUTHENTICATION_FAILED"),
                Arguments.of(new AuthorizationException("Access denied"), 403, "ACCESS_DENIED"),
                Arguments.of(new TooManyFailedLoginAttemptsException("Retry later"),
                        429, "TOO_MANY_FAILED_LOGIN_ATTEMPTS")
        );
    }

    @Test
    void shouldMapEveryErrorCode() {
        // Guards future ErrorCode additions even if the CsvSource above is
        // not extended: the mapper's static guard fails the class load, and
        // this test names the missing constant.
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(BusinessErrorHttpMapper.toStatus(code)).isNotNull();
        }
    }

    @Test
    void shouldMapEveryBusinessFailureKind() {
        for (BusinessFailureKind kind : BusinessFailureKind.values()) {
            BusinessException exception = new BusinessException("error.test", null, "test") {
                @Override
                public BusinessFailureKind getFailureKind() {
                    return kind;
                }
            };
            assertThat(BusinessErrorHttpMapper.toStatus(exception)).isNotNull();
        }
    }

    @RestController
    @Profile("standalone-mvc-test-only")
    static class FailureController {
        private final BusinessException exception;

        FailureController(BusinessException exception) {
            this.exception = exception;
        }

        @GetMapping("/failure")
        void fail() {
            throw exception;
        }
    }
}
