package com.bank.app.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountId value object")
class AccountIdTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create with valid value")
        void shouldCreateWithValidValue() {
            AccountId id = new AccountId(1L);
            assertThat(id.value()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNull() {
            assertThatThrownBy(() -> new AccountId(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Account ID must not be null");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal for same value")
        void shouldBeEqual() {
            AccountId id1 = new AccountId(1L);
            AccountId id2 = new AccountId(1L);
            assertThat(id1).isEqualTo(id2);
            assertThat(id1).hasSameHashCodeAs(id2);
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqual() {
            AccountId id1 = new AccountId(1L);
            AccountId id2 = new AccountId(2L);
            assertThat(id1).isNotEqualTo(id2);
        }
    }
}
