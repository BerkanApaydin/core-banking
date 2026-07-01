package com.bank.app.common.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessException")
@SuppressWarnings("null")
class BusinessExceptionTest {

    @Nested
    @DisplayName("error code from message key")
    class ErrorCodeFromMessageKey {

        @Test
        @DisplayName("should derive error code from simple message key")
        void shouldReturnErrorCodeFromMessageKey() {
            BusinessException ex = new BusinessException("error.test_error", new Object[]{}, "Test") {};
            assertThat(ex.getErrorCode()).isEqualTo("TEST_ERROR");
        }

        @Test
        @DisplayName("should derive error code from multi-part message key")
        void shouldReturnErrorCodeFromMultiPartKey() {
            BusinessException ex = new BusinessException("error.account_not_found_iban", new Object[]{}, "Test") {};
            assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND_IBAN");
        }
    }

    @Nested
    @DisplayName("error code from concrete subclass")
    class ErrorCodeFromConcreteSubclass {

        @Test
        @DisplayName("CurrencyMismatchException should return CURRENCY_MISMATCH")
        void shouldReturnForCurrencyMismatch() {
            CurrencyMismatchException ex = new CurrencyMismatchException("test");
            assertThat(ex.getErrorCode()).isEqualTo("CURRENCY_MISMATCH");
        }

        @Test
        @DisplayName("InvalidIbanException should return INVALID_IBAN")
        void shouldReturnForInvalidIban() {
            InvalidIbanException ex = new InvalidIbanException("test");
            assertThat(ex.getErrorCode()).isEqualTo("INVALID_IBAN");
        }

        @Test
        @DisplayName("ConcurrentRequestException should return CONCURRENT_REQUEST")
        void shouldReturnForConcurrentRequest() {
            ConcurrentRequestException ex = new ConcurrentRequestException("test");
            assertThat(ex.getErrorCode()).isEqualTo("CONCURRENT_REQUEST");
        }
    }

    @Nested
    @DisplayName("message handling")
    class MessageHandling {

        @Test
        @DisplayName("should return message when provided directly")
        void shouldReturnMessageWhenProvided() {
            BusinessException ex = new BusinessException("Direct message") {};
            assertThat(ex.getMessage()).isEqualTo("Direct message");
            assertThat(ex.getMessageKey()).isNull();
            assertThat(ex.getArgs()).isNull();
        }

        @Test
        @DisplayName("should return message from key and args")
        void shouldReturnMessageFromKeyAndArgs() {
            BusinessException ex = new BusinessException("error.test", new Object[]{"arg1"}, "Default: arg1") {};
            assertThat(ex.getMessage()).isEqualTo("Default: arg1");
            assertThat(ex.getMessageKey()).isEqualTo("error.test");
            assertThat(ex.getArgs()).containsExactly("arg1");
        }

        @Test
        @DisplayName("abstract anonymous class should return empty string for error code")
        void shouldReturnEmptyErrorCodeForAnonymousClass() {
            BusinessException ex = new BusinessException("Test message") {};
            assertThat(ex.getErrorCode()).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("HTTP status code")
    class HttpStatusCode {

        @Test
        @DisplayName("should return 400 as default HTTP status code")
        void shouldReturnDefaultHttpStatusCode() {
            BusinessException ex = new BusinessException("Test message") {};
            assertThat(ex.getHttpStatusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("ConcurrentRequestException should return 409")
        void shouldReturn409ForConcurrentRequest() {
            ConcurrentRequestException ex = new ConcurrentRequestException("test");
            assertThat(ex.getHttpStatusCode()).isEqualTo(409);
        }
    }

    @Nested
    @DisplayName("constructor with cause")
    class ConstructorWithCause {

        @Test
        @DisplayName("should create with message key, args, default message and cause")
        void shouldCreateWithCause() {
            Throwable cause = new RuntimeException("root cause");
            BusinessException ex = new BusinessException("error.test", new Object[]{"arg1"}, "Default: arg1", cause) {};
            assertThat(ex.getMessage()).isEqualTo("Default: arg1");
            assertThat(ex.getMessageKey()).isEqualTo("error.test");
            assertThat(ex.getArgs()).containsExactly("arg1");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}
