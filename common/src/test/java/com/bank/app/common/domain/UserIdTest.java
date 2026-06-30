package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class UserIdTest {

    @Test
    void shouldCreateWithValidValue() {
        UserId userId = new UserId(42L);
        assertEquals(42L, userId.value());
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        assertThrows(NullPointerException.class, () -> new UserId(null));
    }

    @Test
    void shouldThrowWhenValueIsZero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new UserId(0L));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void shouldThrowWhenValueIsNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new UserId(-1L));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        assertEquals(new UserId(1L), new UserId(1L));
    }

    @Test
    void shouldNotBeEqualWhenDifferentValue() {
        assertNotEquals(new UserId(1L), new UserId(2L));
    }

    @Test
    void shouldHaveSameHashCodeWhenSameValue() {
        assertEquals(new UserId(1L).hashCode(), new UserId(1L).hashCode());
    }

    @Test
    void shouldReturnStringRepresentation() {
        assertEquals("42", new UserId(42L).toString());
    }
}
