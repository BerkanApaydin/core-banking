package com.bank.app.transfer.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferIdTest {

    @Test
    void shouldCreateWithValidValue() {
        TransferId transferId = new TransferId(42L);
        assertEquals(42L, transferId.value());
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferId(null));
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        assertEquals(new TransferId(1L), new TransferId(1L));
    }

    @Test
    void shouldNotBeEqualWhenDifferentValue() {
        assertNotEquals(new TransferId(1L), new TransferId(2L));
    }
}
