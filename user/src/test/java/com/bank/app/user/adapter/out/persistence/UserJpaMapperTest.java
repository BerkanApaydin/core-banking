package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("UserJpaMapper")
class UserJpaMapperTest {

    private UserJpaMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UserJpaMapper();
    }

    @Nested
    @DisplayName("toJpaEntity")
    class ToJpaEntity {

        @Test
        @DisplayName("should map full user with all fields")
        void shouldMapFullUser() {
            User user = new User(new UserId(42L), "johndoe", "secret", Role.ROLE_ADMIN,
                    new EmailAddress("john@example.com"), new PhoneNumber("555-0100"), 1L);

            UserJpaEntity entity = mapper.toJpaEntity(user);

            assertThat(entity.getId()).isEqualTo(42L);
            assertThat(entity.getUsername()).isEqualTo("johndoe");
            assertThat(entity.getPassword()).isEqualTo("secret");
            assertThat(entity.getRole()).isEqualTo("ROLE_ADMIN");
            assertThat(entity.getEmail()).isEqualTo("john@example.com");
            assertThat(entity.getPhone()).isEqualTo("555-0100");
            assertThat(entity.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should map user with null email and phone")
        void shouldMapUserWithNullEmailAndPhone() {
            User user = new User(new UserId(1L), "janedoe", "pass", Role.ROLE_USER, null, null);

            UserJpaEntity entity = mapper.toJpaEntity(user);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getEmail()).isNull();
            assertThat(entity.getPhone()).isNull();
        }

        @Test
        @DisplayName("should map user with null id")
        void shouldMapUserWithNullId() {
            User user = User.create("newuser", "rawpass");

            UserJpaEntity entity = mapper.toJpaEntity(user);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getUsername()).isEqualTo("newuser");
        }

        @Test
        @DisplayName("should throw when user is null")
        void shouldThrowWhenUserIsNull() {
            assertThatThrownBy(() -> mapper.toJpaEntity(null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User must not be null");
        }
    }

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        @DisplayName("should map full entity with all fields")
        void shouldMapFullEntity() {
            UserJpaEntity entity = new UserJpaEntity(42L, "johndoe", "secret", "ROLE_ADMIN",
                    "john@example.com", "555-0100", 1L);

            User user = mapper.toDomain(entity);

            assertThat(user.getId().value()).isEqualTo(42L);
            assertThat(user.getUsername()).isEqualTo("johndoe");
            assertThat(user.getPassword()).isEqualTo("secret");
            assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
            assertThat(user.getEmail().value()).isEqualTo("john@example.com");
            assertThat(user.getPhone().value()).isEqualTo("555-0100");
            assertThat(user.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should map entity with null email and phone")
        void shouldMapEntityWithNullEmailAndPhone() {
            UserJpaEntity entity = new UserJpaEntity(1L, "janedoe", "pass", "ROLE_USER",
                    null, null, null);

            User user = mapper.toDomain(entity);

            assertThat(user.getEmail()).isNull();
            assertThat(user.getPhone()).isNull();
        }

        @Test
        @DisplayName("should throw when entity is null")
        void shouldThrowWhenEntityIsNull() {
            assertThatThrownBy(() -> mapper.toDomain(null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Entity must not be null");
        }
    }

    @Nested
    @DisplayName("updateJpaEntity")
    class UpdateJpaEntity {

        @Test
        @DisplayName("should update entity with full user")
        void shouldUpdateEntity() {
            UserJpaEntity entity = new UserJpaEntity(1L, "oldname", "oldpass", "ROLE_USER",
                    "old@example.com", "555-0000", 0L);
            User user = new User(new UserId(1L), "newname", "newpass", Role.ROLE_ADMIN,
                    new EmailAddress("new@example.com"), new PhoneNumber("555-9999"), 2L);

            mapper.updateJpaEntity(entity, user);

            assertThat(entity.getUsername()).isEqualTo("newname");
            assertThat(entity.getPassword()).isEqualTo("newpass");
            assertThat(entity.getRole()).isEqualTo("ROLE_ADMIN");
            assertThat(entity.getEmail()).isEqualTo("new@example.com");
            assertThat(entity.getPhone()).isEqualTo("555-9999");
            assertThat(entity.getVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should update entity with null email and phone")
        void shouldUpdateEntityWithNullEmailAndPhone() {
            UserJpaEntity entity = new UserJpaEntity(1L, "name", "pass", "ROLE_USER",
                    "old@example.com", "555-0000", null);
            User user = new User(new UserId(1L), "name", "pass", Role.ROLE_USER, null, null);

            mapper.updateJpaEntity(entity, user);

            assertThat(entity.getEmail()).isNull();
            assertThat(entity.getPhone()).isNull();
        }

        @Test
        @DisplayName("should throw when entity is null")
        void shouldThrowWhenEntityIsNull() {
            User user = User.create("testuser", "pass");
            assertThatThrownBy(() -> mapper.updateJpaEntity(null, user))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Entity and User must not be null");
        }

        @Test
        @DisplayName("should throw when user is null")
        void shouldThrowWhenUserIsNull() {
            UserJpaEntity entity = new UserJpaEntity();
            assertThatThrownBy(() -> mapper.updateJpaEntity(entity, null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Entity and User must not be null");
        }
    }
}
