package com.bank.app.user.domain;

import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;

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
            User user = new User(new UserId(1L), "testuser", "$2a$12$testhashedpasswordhash000000000000000001", Role.ROLE_ADMIN);
            assertThat(user.getId().value()).isEqualTo(1L);
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getPassword()).isEqualTo("$2a$12$testhashedpasswordhash000000000000000001");
            assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
        }

        @Test
        @DisplayName("should assign default role when null")
        void shouldAssignDefaultRoleWhenNull() {
            User user = new User(new UserId(1L), "testuser", "$2a$12$testhashedpasswordhash000000000000000001", null);
            assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should assign default role when null in full constructor")
        void shouldAssignDefaultRoleInFullConstructor() {
            User user = new User(new UserId(1L), "testuser", "$2a$12$testpasshash00000000000000000000000001", null, null, null);
            assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        }

        @Test
        @DisplayName("should record registration only after persistence assigns an ID")
        void shouldCreateViaStaticFactory() {
            User user = User.create("testuser", "$2a$12$testrawpasswordhash00000000000000000001");
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("testuser");
            assertThat(user.getPassword()).isEqualTo("$2a$12$testrawpasswordhash00000000000000000001");
            assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
            assertThat(user.getDomainEvents()).isEmpty();
            assertThatThrownBy(() -> user.recordRegistration(Clock.systemUTC()))
                    .isInstanceOf(IllegalStateException.class);

            User persisted = new User(new UserId(42L), user.getUsername(), user.getPassword(), user.getRole());
            persisted.recordRegistration(Clock.systemUTC());
            assertThat(persisted.getDomainEvents()).hasSize(1);
            assertThat(((UserRegisteredEvent) persisted.getDomainEvents().get(0)).userId()).isEqualTo("42");
        }

        @Test
        @DisplayName("should create with email and phone via constructor")
        void shouldCreateWithEmailAndPhone() {
            User user = new User(new UserId(1L), "testuser", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER,
                    new EmailAddress("test@example.com"), new PhoneNumber("555-0100"));
            assertThat(user.getEmail().value()).isEqualTo("test@example.com");
            assertThat(user.getPhone().value()).isEqualTo("555-0100");
        }

        @Test
        @DisplayName("should create with email and phone via static factory")
        void shouldCreateViaStaticFactoryWithEmailAndPhone() {
            User user = User.create("testuser", "$2a$12$testpasshash00000000000000000000000001", new EmailAddress("test@example.com"),
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
            assertThatThrownBy(() -> new User(new UserId(1L), null, "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new User(new UserId(1L), "testuser", null, Role.ROLE_USER))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank username")
        void shouldRejectBlankUsername() {
            assertThatThrownBy(() -> new User(new UserId(1L), "   ", "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username must not be empty");
        }

        @Test
        @DisplayName("should reject empty username")
        void shouldRejectEmptyUsername() {
            assertThatThrownBy(() -> new User(new UserId(1L), "", "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username must not be empty");
        }

        @Test
        @DisplayName("should reject username longer than 255 characters")
        void shouldRejectLongUsername() {
            String longUsername = "a".repeat(256);
            assertThatThrownBy(() -> new User(new UserId(1L), longUsername, "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username can be at most 255 characters");
        }

        @Test
        @DisplayName("should allow username exactly 255 characters")
        void shouldAllow255CharUsername() {
            String username = "a".repeat(255);
            User user = new User(new UserId(1L), username, "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER);
            assertThat(user.getUsername()).isEqualTo(username);
        }

        @Test
        @DisplayName("should trim username on creation")
        void shouldTrimUsername() {
            User user = new User(new UserId(1L), "  spaceduser  ", "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER);
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
            assertThat(user.getTokenVersion()).isZero();
            user.changePassword("$2a$12$testnewencodedpasswordhash00000000000001");
            assertThat(user.getPassword()).isEqualTo("$2a$12$testnewencodedpasswordhash00000000000001");
            assertThat(user.getTokenVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should reject null password on change")
        void shouldRejectNullPassword() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThatThrownBy(() -> user.changePassword((String) null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("New password must not be null");
        }

        @Test
        @DisplayName("should reject blank password on change")
        void shouldRejectBlankPassword() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThatThrownBy(() -> user.changePassword("   "))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password must not be empty");
        }

        @Test
        @DisplayName("should bump token version on EncodedPassword change")
        void shouldBumpVersionOnEncodedChange() {
            // Kills the MATH mutant (tokenVersion++ vs --) on the
            // EncodedPassword overload: only the increment retires sessions.
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThat(user.getTokenVersion()).isZero();
            user.changePassword(EncodedPassword
                    .of("$2a$12$testnewencodedpasswordhash00000000000001"));
            assertThat(user.getTokenVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should update email")
        void shouldUpdateEmail() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            user.updateEmail(new EmailAddress("new@example.com"));
            assertThat(user.getEmail().value()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("should reject null email")
        void shouldRejectNullEmail() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThatThrownBy(() -> user.updateEmail(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Email must not be null");
        }

        @Test
        @DisplayName("should update phone")
        void shouldUpdatePhone() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            user.updatePhone(new PhoneNumber("555-0200"));
            assertThat(user.getPhone().value()).isEqualTo("555-0200");
        }

        @Test
        @DisplayName("should reject null phone")
        void shouldRejectNullPhone() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThatThrownBy(() -> user.updatePhone(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Phone must not be null");
        }

        @Test
        @DisplayName("should assign role")
        void shouldAssignRole() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThat(user.getTokenVersion()).isZero();
            user.assignRole(Role.ROLE_ADMIN);
            assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
            assertThat(user.getTokenVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should reject null role")
        void shouldRejectNullRole() {
            User user = User.create("testuser", "$2a$12$testpasswordhash00000000000000000000001");
            assertThatThrownBy(() -> user.assignRole(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Role must not be null");
        }

        @Test
        @DisplayName("hasRole should return true when role matches")
        void hasRoleTrueWhenMatches() {
            User user = new User(new UserId(1L), "admin", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_ADMIN);
            assertThat(user.hasRole(Role.ROLE_ADMIN)).isTrue();
        }

        @Test
        @DisplayName("hasRole should return false when role does not match")
        void hasRoleFalseWhenNotMatch() {
            User user = new User(new UserId(1L), "user", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            assertThat(user.hasRole(Role.ROLE_ADMIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true for same id")
        void equalsSameId() {
            User user1 = new User(new UserId(1L), "TestUser", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            User user2 = new User(new UserId(1L), "testuser", "$2a$12$testpass2hash00000000000000000000000001", Role.ROLE_USER);
            assertThat(user1).isEqualTo(user2);
        }

        @Test
        @DisplayName("equals should return false for different ids")
        void notEqualsWhenDifferentId() {
            User user1 = new User(new UserId(1L), "user1", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            User user2 = new User(new UserId(2L), "user1", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            assertThat(user1).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("equals should return true for same reference")
        void equalsSameReference() {
            User user = new User(new UserId(1L), "testuser", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            assertThat(user).isEqualTo(user);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            User user = new User(new UserId(1L), "testuser", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            assertThat(user).isNotEqualTo("not-a-user");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            User user1 = new User(new UserId(1L), "TestUser", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER);
            User user2 = new User(new UserId(1L), "testuser", "$2a$12$testpass2hash00000000000000000000000001", Role.ROLE_USER);
            assertThat(user1).hasSameHashCodeAs(user2);
        }

        @Test
        @DisplayName("equals should return false when id is null")
        void notEqualsWhenIdNull() {
            User user1 = User.create("newuser", "$2a$12$testpasshash00000000000000000000000001");
            User user2 = User.create("newuser", "$2a$12$testpasshash00000000000000000000000001");
            assertThat(user1).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("hashCode should return 0 when id is null")
        void hashCodeWhenIdNull() {
            User user = User.create("newuser", "$2a$12$testpasshash00000000000000000000000001");
            assertThat(user.hashCode()).isZero();
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringMethod {

        @Test
        @DisplayName("should include id, username and role")
        void shouldIncludeKeyFields() {
            User user = new User(new UserId(5L), "testuser", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_ADMIN);
            assertThat(user).hasToString("User{id=5, username='testuser', role='ROLE_ADMIN'}");
        }
    }
}
