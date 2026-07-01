package com.bank.app.common.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConcurrentRequestException")
@SuppressWarnings("null")
class ConcurrentRequestExceptionTest {

    @Nested
    @DisplayName("constructors")
    class Constructors {

        @Test
        @DisplayName("should create with direct message")
        void shouldCreateWithDirectMessage() {
            ConcurrentRequestException ex = new ConcurrentRequestException("concurrent");
            assertThat(ex.getMessage()).isEqualTo("concurrent");
            assertThat(ex.getMessageKey()).isNull();
            assertThat(ex.getArgs()).isNull();
        }

        @Test
        @DisplayName("should create with message key, args and default message")
        void shouldCreateWithKeyAndArgs() {
            ConcurrentRequestException ex = new ConcurrentRequestException("key", new Object[]{"arg"}, "default");
            assertThat(ex.getMessage()).isEqualTo("default");
            assertThat(ex.getMessageKey()).isEqualTo("key");
            assertThat(ex.getArgs()).containsExactly("arg");
        }
    }

    @Nested
    @DisplayName("HTTP status code")
    class HttpStatusCode {

        @Test
        @DisplayName("should return 409 Conflict")
        void shouldReturn409() {
            ConcurrentRequestException ex = new ConcurrentRequestException("concurrent");
            assertThat(ex.getHttpStatusCode()).isEqualTo(409);
        }
    }
}
