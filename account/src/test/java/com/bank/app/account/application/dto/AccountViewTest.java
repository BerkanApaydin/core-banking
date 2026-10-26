package com.bank.app.account.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountViewTest {

    @Test
    void shouldExposeAllFields() {
        AccountView view = new AccountView(1L, 10L, "TRY", "ACTIVE");

        assertEquals(1L, view.id());
        assertEquals(10L, view.userId());
        assertEquals("TRY", view.currency());
        assertEquals("ACTIVE", view.status());
    }

    @Test
    void shouldRejectNullFields() {
        assertThrows(NullPointerException.class,
                () -> new AccountView(null, 10L, "TRY", "ACTIVE"));
        assertThrows(NullPointerException.class,
                () -> new AccountView(1L, null, "TRY", "ACTIVE"));
        assertThrows(NullPointerException.class,
                () -> new AccountView(1L, 10L, null, "ACTIVE"));
        assertThrows(NullPointerException.class,
                () -> new AccountView(1L, 10L, "TRY", null));
    }
}
