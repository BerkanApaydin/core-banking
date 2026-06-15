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
}
