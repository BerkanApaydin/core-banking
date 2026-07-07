package com.bank.app.transfer.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferStatusTest {

    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void shouldSupportRoundTripViaValueOf(TransferStatus status) {
        assertSame(status, TransferStatus.valueOf(status.name()));
    }

    @Test
    void shouldHaveExactlyFourStatuses() {
        assertEquals(4, TransferStatus.values().length);
    }
}
