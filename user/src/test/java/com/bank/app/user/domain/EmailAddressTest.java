package com.bank.app.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("EmailAddress value object")
class EmailAddressTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create valid email")
        void shouldCreateValidEmail() {
            EmailAddress email = new EmailAddress("test@example.com");
            assertThat(email.value()).isEqualTo("test@example.com");
        }

        @ParameterizedTest(name = "should accept valid email: {0}")
        @ValueSource(strings = {
                "user@domain.com",
                "user.name+tag@domain.co.uk",
                "user_name@domain.org",
                "user-name@domain.net",
                "a@b.co",
                "123@domain.com"
        })
        void shouldAcceptValidEmails(String email) {
            assertThat(new EmailAddress(email).value()).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should reject null email")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new EmailAddress(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Email must not be null");
        }

        @ParameterizedTest(name = "should reject invalid email: {0}")
        @ValueSource(strings = {
                "invalid",
                "@domain.com",
                "user@",
                "user@.com",
                "user@domain",
                "user@domain.",
                " ",
                ""
        })
        void shouldRejectInvalidEmails(String email) {
            assertThatThrownBy(() -> new EmailAddress(email))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid email format");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        void shouldMaskLocalPart() {
            EmailAddress email = new EmailAddress("ahmet.yilmaz@example.com");
            assertThat(email.toString()).isEqualTo("a***z@example.com");
        }

        @Test
        void shouldHandleShortLocalPart() {
            EmailAddress email = new EmailAddress("ab@test.com");
            assertThat(email.toString()).isEqualTo("a***b@test.com");
        }

        @Test
        void shouldNotMaskSingleCharLocalPart() {
            EmailAddress email = new EmailAddress("a@test.com");
            assertThat(email.toString()).isEqualTo("a@test.com");
        }
    }
}
