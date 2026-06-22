package com.bank.app.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("User domain entity")
class UserTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreateSuccessfully() {
            User user = new User(1L, "testuser", "hashed_password", "ROLE_ADMIN");
            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getPassword()).isEqualTo("hashed_password");
            assertThat(user.getRole()).isEqualTo("ROLE_ADMIN");
        }

        @Test
        @DisplayName("should assign default role when null")
        void shouldAssignDefaultRoleWhenNull() {
            User user = new User(1L, "testuser", "hashed_password", null);
            assertThat(user.getRole()).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("should assign default role when null in full constructor")
        void shouldAssignDefaultRoleInFullConstructor() {
            User user = new User(1L, "testuser", "pass", null, null, null);
            assertThat(user.getRole()).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("should create via static factory")
        void shouldCreateViaStaticFactory() {
            User user = User.create("testuser", "raw_password");
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getPassword()).isEqualTo("raw_password");
            assertThat(user.getRole()).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("should create with email and phone via constructor")
        void shouldCreateWithEmailAndPhone() {
            User user = new User(1L, "testuser", "pass", "ROLE_USER", "test@example.com", "555-0100");
            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getPhone()).isEqualTo("555-0100");
        }

        @Test
        @DisplayName("should create with email and phone via static factory")
        void shouldCreateViaStaticFactoryWithEmailAndPhone() {
            User user = User.create("testuser", "pass", "test@example.com", "555-0100");
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getPhone()).isEqualTo("555-0100");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @ParameterizedTest(name = "should reject null {0}")
        @NullSource
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullArgs(String nullValue) {
            assertThatThrownBy(() -> new User(1L, null, "password", "ROLE_USER"))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new User(1L, "testuser", null, "ROLE_USER"))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true for case-insensitive same username")
        void equalsCaseInsensitive() {
            User user1 = new User(1L, "TestUser", "pass", "ROLE_USER");
            User user2 = new User(2L, "testuser", "pass2", "ROLE_USER");
            assertThat(user1).isEqualTo(user2);
        }

        @Test
        @DisplayName("equals should return false for different usernames")
        void notEqualsWhenDifferentUsername() {
            User user1 = new User(1L, "user1", "pass", "ROLE_USER");
            User user2 = new User(2L, "user2", "pass", "ROLE_USER");
            assertThat(user1).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("equals should return true for same reference")
        void equalsSameReference() {
            User user = new User(1L, "testuser", "pass", "ROLE_USER");
            assertThat(user).isEqualTo(user);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            User user = new User(1L, "testuser", "pass", "ROLE_USER");
            assertThat(user).isNotEqualTo("not-a-user");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            User user1 = new User(1L, "TestUser", "pass", "ROLE_USER");
            User user2 = new User(2L, "testuser", "pass2", "ROLE_USER");
            assertThat(user1).hasSameHashCodeAs(user2);
        }
    }
}
