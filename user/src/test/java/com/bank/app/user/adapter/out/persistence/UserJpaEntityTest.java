package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class UserJpaEntityTest {

    @Test
    void shouldCreateUserJpaEntity() {
        UserJpaEntity entity = new UserJpaEntity(1L, "user", "$2a$12$testpasshash00000000000000000000000001", Role.ROLE_USER, null, null, null);

        assertEquals(1L, entity.getId());
        assertEquals("user", entity.getUsername());
        assertEquals("$2a$12$testpasshash00000000000000000000000001", entity.getPassword());
        assertEquals(Role.ROLE_USER, entity.getRole());

        UserJpaEntity empty = new UserJpaEntity();
        empty.setId(2L);
        empty.setUsername("user2");
        empty.setPassword("$2a$12$testpass2hash00000000000000000000000001");
        empty.setRole(Role.ROLE_ADMIN);

        assertEquals(2L, empty.getId());
        assertEquals("user2", empty.getUsername());
        assertEquals("$2a$12$testpass2hash00000000000000000000000001", empty.getPassword());
        assertEquals(Role.ROLE_ADMIN, empty.getRole());
    }
}
