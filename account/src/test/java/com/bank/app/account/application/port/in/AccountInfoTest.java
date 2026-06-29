package com.bank.app.account.application.port.in;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountInfoTest {

    @Test
    void shouldCreateWithAllFields() {
        AccountInfo info = new AccountInfo(1L, 10L, "TRY", "ACTIVE");
        assertEquals(1L, info.id());
        assertEquals(10L, info.userId());
        assertEquals("TRY", info.currency());
        assertEquals("ACTIVE", info.status());
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(NullPointerException.class,
                () -> new AccountInfo(null, 10L, "TRY", "ACTIVE"));
    }

    @Test
    void shouldRejectNullUserId() {
        assertThrows(NullPointerException.class,
                () -> new AccountInfo(1L, null, "TRY", "ACTIVE"));
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(NullPointerException.class,
                () -> new AccountInfo(1L, 10L, null, "ACTIVE"));
    }

    @Test
    void shouldRejectNullStatus() {
        assertThrows(NullPointerException.class,
                () -> new AccountInfo(1L, 10L, "TRY", null));
    }
}
