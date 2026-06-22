package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void shouldCreateUserSuccessfully() {
        User user = new User(1L, "testuser", "hashed_password", "ROLE_ADMIN");

        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("hashed_password", user.getPassword());
        assertEquals("ROLE_ADMIN", user.getRole());
    }

    @Test
    void shouldCreateUserWithDefaultRoleWhenRoleIsNull() {
        User user = new User(1L, "testuser", "hashed_password", null);

        assertEquals("ROLE_USER", user.getRole());
    }

    @Test
    void shouldCreateUserSuccessfullyWithStaticFactory() {
        User user = User.create("testuser", "raw_password");

        assertNull(user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("raw_password", user.getPassword());
        assertEquals("ROLE_USER", user.getRole());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenUsernameIsNull() {
        assertThrows(NullPointerException.class, () -> 
                new User(1L, null, "password", "ROLE_USER")
        );
    }

    @Test
    void shouldThrowNullPointerExceptionWhenPasswordIsNull() {
        assertThrows(NullPointerException.class, () -> 
                new User(1L, "testuser", null, "ROLE_USER")
        );
    }

    @Test
    void equalsShouldReturnTrueWhenSameUsernameCaseInsensitive() {
        User user1 = new User(1L, "TestUser", "pass", "ROLE_USER");
        User user2 = new User(2L, "testuser", "pass2", "ROLE_USER");
        assertEquals(user1, user2);
    }

    @Test
    void equalsShouldReturnFalseWhenDifferentUsername() {
        User user1 = new User(1L, "user1", "pass", "ROLE_USER");
        User user2 = new User(2L, "user2", "pass", "ROLE_USER");
        assertNotEquals(user1, user2);
    }

    @Test
    void equalsShouldReturnTrueWhenSameReference() {
        User user = new User(1L, "testuser", "pass", "ROLE_USER");
        assertEquals(user, user);
    }

    @Test
    void equalsShouldReturnFalseWhenOtherObject() {
        User user = new User(1L, "testuser", "pass", "ROLE_USER");
        assertNotEquals("not-a-user", user);
    }

    @Test
    void hashCodeShouldBeConsistentWithEquals() {
        User user1 = new User(1L, "TestUser", "pass", "ROLE_USER");
        User user2 = new User(2L, "testuser", "pass2", "ROLE_USER");
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void shouldCreateUserWithEmailAndPhone() {
        User user = new User(1L, "testuser", "pass", "ROLE_USER", "test@example.com", "555-0100");
        assertEquals("test@example.com", user.getEmail());
        assertEquals("555-0100", user.getPhone());
    }

    @Test
    void shouldCreateUserWithStaticFactoryWithEmailAndPhone() {
        User user = User.create("testuser", "pass", "test@example.com", "555-0100");
        assertNull(user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("555-0100", user.getPhone());
    }

    @Test
    void shouldReturnDefaultRoleWhenRoleIsNullInFullConstructor() {
        User user = new User(1L, "testuser", "pass", null, null, null);
        assertEquals("ROLE_USER", user.getRole());
    }
}
