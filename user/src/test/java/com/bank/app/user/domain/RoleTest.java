package com.bank.app.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("Role enum")
class RoleTest {

    @Nested
    @DisplayName("fromString")
    class FromString {

        @Test
        @DisplayName("should return ROLE_USER for valid ROLE_USER string")
        void shouldReturnForRoleUser() {
            assertThat(Role.fromString("ROLE_USER")).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should return ROLE_ADMIN for valid ROLE_ADMIN string")
        void shouldReturnForRoleAdmin() {
            assertThat(Role.fromString("ROLE_ADMIN")).isEqualTo(Role.ROLE_ADMIN);
        }

        @ParameterizedTest(name = "should return ROLE_USER for blank/null input: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        void shouldReturnDefaultForBlank(String value) {
            assertThat(Role.fromString(value)).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should throw for invalid role string")
        void shouldThrowForInvalidRole() {
            assertThatThrownBy(() -> Role.fromString("INVALID_ROLE"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Geçersiz rol");
        }

        @Test
        @DisplayName("should throw for lowercase string")
        void shouldThrowForLowercase() {
            assertThatThrownBy(() -> Role.fromString("role_user"))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}
