package com.bank.app.user.domain;

import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("User domain entity")
class UserTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreateSuccessfully() {
            User user = new User(new UserId(1L), "testuser", "hashed_password", Role.ROLE_ADMIN);
            assertThat(user.getId().value()).isEqualTo(1L);
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getPassword()).isEqualTo("hashed_password");
            assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
        }

        @Test
        @DisplayName("should assign default role when null")
        void shouldAssignDefaultRoleWhenNull() {
            User user = new User(new UserId(1L), "testuser", "hashed_password", null);
            assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should assign default role when null in full constructor")
        void shouldAssignDefaultRoleInFullConstructor() {
            User user = new User(new UserId(1L), "testuser", "pass", null, null, null);
            assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should create via static factory")
        void shouldCreateViaStaticFactory() {
            User user = User.create("testuser", "raw_password");
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getPassword()).isEqualTo("raw_password");
            assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should create with email and phone via constructor")
        void shouldCreateWithEmailAndPhone() {
            User user = new User(new UserId(1L), "testuser", "pass", Role.ROLE_USER,
                    new EmailAddress("test@example.com"), new PhoneNumber("555-0100"));
            assertThat(user.getEmail().value()).isEqualTo("test@example.com");
            assertThat(user.getPhone().value()).isEqualTo("555-0100");
        }

        @Test
        @DisplayName("should create with email and phone via static factory")
        void shouldCreateViaStaticFactoryWithEmailAndPhone() {
            User user = User.create("testuser", "pass", new EmailAddress("test@example.com"),
                    new PhoneNumber("555-0100"));
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getEmail().value()).isEqualTo("test@example.com");
            assertThat(user.getPhone().value()).isEqualTo("555-0100");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullArgs() {
            assertThatThrownBy(() -> new User(new UserId(1L), null, "password", Role.ROLE_USER))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new User(new UserId(1L), "testuser", null, Role.ROLE_USER))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank username")
        void shouldRejectBlankUsername() {
            assertThatThrownBy(() -> new User(new UserId(1L), "   ", "password", Role.ROLE_USER))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username must not be empty");
        }

        @Test
        @DisplayName("should reject empty username")
        void shouldRejectEmptyUsername() {
            assertThatThrownBy(() -> new User(new UserId(1L), "", "password", Role.ROLE_USER))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username must not be empty");
        }

        @Test
        @DisplayName("should reject username longer than 255 characters")
        void shouldRejectLongUsername() {
            String longUsername = "a".repeat(256);
            assertThatThrownBy(() -> new User(new UserId(1L), longUsername, "password", Role.ROLE_USER))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Kullanıcı adı en fazla 255 karakter olabilir");
        }

        @Test
        @DisplayName("should trim username on creation")
        void shouldTrimUsername() {
            User user = new User(new UserId(1L), "  spaceduser  ", "password", Role.ROLE_USER);
            assertThat(user.getUsername()).isEqualTo("spaceduser");
        }
    }

    @Nested
    @DisplayName("behavioral methods")
    class BehavioralMethods {

        @Test
        @DisplayName("should change password")
        void shouldChangePassword() {
            User user = User.create("testuser", "old_password");
            user.changePassword("new_encoded_password");
            assertThat(user.getPassword()).isEqualTo("new_encoded_password");
        }

        @Test
        @DisplayName("should reject null password on change")
        void shouldRejectNullPassword() {
            User user = User.create("testuser", "password");
            assertThatThrownBy(() -> user.changePassword(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("New password must not be null");
        }

        @Test
        @DisplayName("should reject blank password on change")
        void shouldRejectBlankPassword() {
            User user = User.create("testuser", "password");
            assertThatThrownBy(() -> user.changePassword("   "))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password must not be empty");
        }

        @Test
        @DisplayName("should update email")
        void shouldUpdateEmail() {
            User user = User.create("testuser", "password");
            user.updateEmail(new EmailAddress("new@example.com"));
            assertThat(user.getEmail().value()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("should reject null email")
        void shouldRejectNullEmail() {
            User user = User.create("testuser", "password");
            assertThatThrownBy(() -> user.updateEmail(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Email must not be null");
        }

        @Test
        @DisplayName("should update phone")
        void shouldUpdatePhone() {
            User user = User.create("testuser", "password");
            user.updatePhone(new PhoneNumber("555-0200"));
            assertThat(user.getPhone().value()).isEqualTo("555-0200");
        }

        @Test
        @DisplayName("should reject null phone")
        void shouldRejectNullPhone() {
            User user = User.create("testuser", "password");
            assertThatThrownBy(() -> user.updatePhone(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Phone must not be null");
        }

        @Test
        @DisplayName("should assign role")
        void shouldAssignRole() {
            User user = User.create("testuser", "password");
            user.assignRole(Role.ROLE_ADMIN);
            assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
        }

        @Test
        @DisplayName("should reject null role")
        void shouldRejectNullRole() {
            User user = User.create("testuser", "password");
            assertThatThrownBy(() -> user.assignRole(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Role must not be null");
        }

        @Test
        @DisplayName("hasRole should return true when role matches")
        void hasRoleTrueWhenMatches() {
            User user = new User(new UserId(1L), "admin", "pass", Role.ROLE_ADMIN);
            assertThat(user.hasRole(Role.ROLE_ADMIN)).isTrue();
        }

        @Test
        @DisplayName("hasRole should return false when role does not match")
        void hasRoleFalseWhenNotMatch() {
            User user = new User(new UserId(1L), "user", "pass", Role.ROLE_USER);
            assertThat(user.hasRole(Role.ROLE_ADMIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true for same id")
        void equalsSameId() {
            User user1 = new User(new UserId(1L), "TestUser", "pass", Role.ROLE_USER);
            User user2 = new User(new UserId(1L), "testuser", "pass2", Role.ROLE_USER);
            assertThat(user1).isEqualTo(user2);
        }

        @Test
        @DisplayName("equals should return false for different ids")
        void notEqualsWhenDifferentId() {
            User user1 = new User(new UserId(1L), "user1", "pass", Role.ROLE_USER);
            User user2 = new User(new UserId(2L), "user1", "pass", Role.ROLE_USER);
            assertThat(user1).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("equals should return true for same reference")
        void equalsSameReference() {
            User user = new User(new UserId(1L), "testuser", "pass", Role.ROLE_USER);
            assertThat(user).isEqualTo(user);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            User user = new User(new UserId(1L), "testuser", "pass", Role.ROLE_USER);
            assertThat(user).isNotEqualTo("not-a-user");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            User user1 = new User(new UserId(1L), "TestUser", "pass", Role.ROLE_USER);
            User user2 = new User(new UserId(1L), "testuser", "pass2", Role.ROLE_USER);
            assertThat(user1).hasSameHashCodeAs(user2);
        }

        @Test
        @DisplayName("equals should return false when id is null")
        void notEqualsWhenIdNull() {
            User user1 = User.create("newuser", "pass");
            User user2 = User.create("newuser", "pass");
            assertThat(user1).isNotEqualTo(user2);
        }
    }
}
