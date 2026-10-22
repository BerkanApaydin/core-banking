package com.bank.app.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("PhoneNumber value object")
class PhoneNumberTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create valid phone number")
        void shouldCreateValidPhone() {
            PhoneNumber phone = new PhoneNumber("555-0100");
            assertThat(phone.value()).isEqualTo("555-0100");
        }

        @ParameterizedTest(name = "should accept valid phone: {0}")
        @ValueSource(strings = {
                "555-0100",
                "+1-555-0100",
                "905551234567",
                "+905551234567",
                "555 0100",
                "123456",
                "12345678901234567890"
        })
        void shouldAcceptValidPhones(String phone) {
            assertThat(new PhoneNumber(phone).value()).isEqualTo(phone);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should reject null phone number")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new PhoneNumber(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Phone number must not be null");
        }

        @ParameterizedTest(name = "should reject invalid phone: {0}")
        @ValueSource(strings = {
                "",
                " ",
                "ab",
                "123",
                "@@@@"
        })
        void shouldRejectInvalidPhones(String phone) {
            assertThatThrownBy(() -> new PhoneNumber(phone))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid phone number format");
        }

        @Test
        @DisplayName("should reject separator-only input that passes the length check")
        void shouldRejectWithoutEnoughDigits() {
            assertThatThrownBy(() -> new PhoneNumber("+-.-.-"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid phone number format");
        }

        @Test
        @DisplayName("should pin the six-digit boundary")
        void shouldPinTheSixDigitBoundary() {
            // The rule is ">= 6 digits", not ">= 6 characters": six digits are
            // the shortest acceptable number.
            assertThat(new PhoneNumber("123456").value()).isEqualTo("123456");

            assertThatThrownBy(() -> new PhoneNumber("12345"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid phone number format");
        }

        @Test
        @DisplayName("should reject twenty-one characters")
        void shouldRejectTwentyOneCharacters() {
            // The column is VARCHAR(20): a 21-char value would be truncated
            // or rejected by the database instead of at the boundary.
            String tooLong = "123456789012345678901";

            assertThatThrownBy(() -> new PhoneNumber(tooLong))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid phone number format");
        }

        @Test
        @DisplayName("should reject a twenty-character value with only five digits")
        void shouldRejectTwentyCharactersWithFiveDigits() {
            // Length is fine, the digit count is not: the length check alone
            // must not let a separator-only value through.
            String fiveDigitsInTwentyChars = "12345" + "-".repeat(15);
            assertThat(fiveDigitsInTwentyChars).hasSize(20);

            assertThatThrownBy(() -> new PhoneNumber(fiveDigitsInTwentyChars))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid phone number format");
        }

        @Test
        @DisplayName("should trim surrounding whitespace")
        void shouldTrimSurroundingWhitespace() {
            // A pasted phone number with padding must reach the notification
            // channel without the spaces, or the SMS gateway rejects it.
            PhoneNumber phone = new PhoneNumber("  +905551234567  ");

            assertThat(phone.value()).isEqualTo("+905551234567");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        void shouldMaskMiddleDigits() {
            PhoneNumber phone = new PhoneNumber("+905551234567");
            assertThat(phone.toString()).isEqualTo("**********567");
        }

        @Test
        void shouldHandleShortNumber() {
            PhoneNumber phone = new PhoneNumber("123456");
            assertThat(phone.toString()).isEqualTo("***456");
        }


    }
}
