package com.bank.app.user.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserJpaEntityTest {

    @Test
    void shouldCreateUserJpaEntity() {
        UserJpaEntity entity = new UserJpaEntity(1L, "user", "pass", "role");

        assertEquals(1L, entity.getId());
        assertEquals("user", entity.getUsername());
        assertEquals("pass", entity.getPassword());
        assertEquals("role", entity.getRole());

        UserJpaEntity empty = new UserJpaEntity();
        empty.setId(2L);
        empty.setUsername("user2");
        empty.setPassword("pass2");
        empty.setRole("role2");

        assertEquals(2L, empty.getId());
        assertEquals("user2", empty.getUsername());
        assertEquals("pass2", empty.getPassword());
        assertEquals("role2", empty.getRole());
    }
}
