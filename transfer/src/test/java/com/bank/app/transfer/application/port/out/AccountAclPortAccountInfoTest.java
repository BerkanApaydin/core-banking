package com.bank.app.transfer.application.port.out;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountAclPortAccountInfoTest {

    @Test
    void shouldCreateAccountInfoWithValidFields() {
        AccountAclPort.AccountInfo info = new AccountAclPort.AccountInfo(1L, 100L, "TRY", "ACTIVE");
        assertEquals(1L, info.id());
        assertEquals(100L, info.userId());
        assertEquals("TRY", info.currency());
        assertEquals("ACTIVE", info.status());
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountAclPort.AccountInfo(null, 100L, "TRY", "ACTIVE"));
    }

    @Test
    void shouldThrowWhenUserIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountAclPort.AccountInfo(1L, null, "TRY", "ACTIVE"));
    }

    @Test
    void shouldThrowWhenCurrencyIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountAclPort.AccountInfo(1L, 100L, null, "ACTIVE"));
    }

    @Test
    void shouldThrowWhenStatusIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountAclPort.AccountInfo(1L, 100L, "TRY", null));
    }
}
