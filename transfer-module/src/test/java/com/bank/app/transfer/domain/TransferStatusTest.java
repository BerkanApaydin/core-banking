package com.bank.app.transfer.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferStatusTest {

    @Test
    void shouldHavePendingStatus() {
        assertEquals("PENDING", TransferStatus.PENDING.name());
    }

    @Test
    void shouldHaveCompletedStatus() {
        assertEquals("COMPLETED", TransferStatus.COMPLETED.name());
    }

    @Test
    void shouldHaveFailedStatus() {
        assertEquals("FAILED", TransferStatus.FAILED.name());
    }

    @Test
    void shouldHaveCancelledStatus() {
        assertEquals("CANCELLED", TransferStatus.CANCELLED.name());
    }

    @Test
    void shouldHaveFourValues() {
        assertEquals(4, TransferStatus.values().length);
    }
}
