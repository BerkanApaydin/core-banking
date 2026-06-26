package com.bank.app.account.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountStatusTest {

    @Test
    void shouldHaveActiveStatus() {
        assertEquals("ACTIVE", AccountStatus.ACTIVE.name());
    }

    @Test
    void shouldHaveSuspendedStatus() {
        assertEquals("SUSPENDED", AccountStatus.SUSPENDED.name());
    }

    @Test
    void shouldHaveClosedStatus() {
        assertEquals("CLOSED", AccountStatus.CLOSED.name());
    }

    @Test
    void shouldHaveThreeValues() {
        assertEquals(3, AccountStatus.values().length);
    }
}
