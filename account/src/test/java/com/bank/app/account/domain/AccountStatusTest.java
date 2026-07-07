package com.bank.app.account.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountStatusTest {

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void shouldSupportRoundTripViaValueOf(AccountStatus status) {
        assertSame(status, AccountStatus.valueOf(status.name()));
    }

    @Test
    void shouldHaveExactlyThreeStatuses() {
        assertEquals(3, AccountStatus.values().length);
    }
}
