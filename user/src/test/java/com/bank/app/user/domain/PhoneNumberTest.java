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
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        void shouldMaskMiddleDigits() {
            PhoneNumber phone = new PhoneNumber("+905551234567");
            String masked = phone.toString();
            assertThat(masked).doesNotContain("1234");
            assertThat(masked).endsWith("567");
        }

        @Test
        void shouldHandleShortNumber() {
            PhoneNumber phone = new PhoneNumber("123456");
            assertThat(phone.toString()).isEqualTo("***456");
        }


    }
}
