package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransferEdgeCaseTest {

    @Test
    void shouldCompleteWhenStatusIsPending() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        transfer.complete();
        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());
    }

    @Test
    void shouldThrowWhenCompleteOnAlreadyCompleted() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now());
        assertThrows(IllegalStateException.class, transfer::complete);
    }

    @Test
    void shouldAllowCancellationWithinWindow() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(23));
        assertDoesNotThrow(() -> transfer.cancel(24));
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldDenyCancellationJustAfterWindowBoundary() {
        LocalDateTime createdAt = LocalDateTime.now().minusHours(24).minusMinutes(1);
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, createdAt);
        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));
    }

    @Test
    void shouldDenyCancellationWhenWindowIsZeroAndTransferIsOld() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now().minusMinutes(1));
        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(0));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));
    }

    @Test
    void shouldAllowCancellationWithZeroWindowWhenJustCreated() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now());
        transfer.cancel(0);
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldThrowWhenCancellingFailedTransfer() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.FAILED, LocalDateTime.now());
        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("Sadece tamamlanmış transferler iptal edilebilir"));
    }

    @Test
    void shouldThrowWhenCancellingPendingTransfer() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("Sadece tamamlanmış transferler iptal edilebilir"));
    }

    @Test
    void shouldThrowWhenCancellingAlreadyCancelledTransfer() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.CANCELLED, LocalDateTime.now());
        TransferAlreadyCancelledException ex = assertThrows(TransferAlreadyCancelledException.class,
                () -> transfer.cancel(24));
        assertEquals("Transfer zaten iptal edilmiş. ID: 1", ex.getMessage());
    }

    @Test
    void shouldCreateTransferWithPendingStatus() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        assertNotNull(transfer.getCreatedAt());
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
        assertNull(transfer.getId());
    }

    @Test
    void shouldThrowWhenCompleteOnFailedStatusWithExactMessage() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.FAILED, LocalDateTime.now());
        IllegalStateException ex = assertThrows(IllegalStateException.class, transfer::complete);
        assertTrue(ex.getMessage().contains("Sadece PENDING durumundaki transferler tamamlanabilir"));
        assertEquals("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: FAILED",
                ex.getMessage());
    }

    @Test
    void shouldThrowWhenCompleteOnCancelledStatusWithExactMessage() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.CANCELLED, LocalDateTime.now());
        IllegalStateException ex = assertThrows(IllegalStateException.class, transfer::complete);
        assertTrue(ex.getMessage().contains("Sadece PENDING durumundaki transferler tamamlanabilir"));
        assertEquals("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: CANCELLED",
                ex.getMessage());
    }

    @Test
    void shouldCreateTransferWithVersion() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.PENDING, LocalDateTime.now(), 5L);
        assertEquals(5L, transfer.getVersion());
    }

    @Test
    void shouldCreateTransferWithNullVersionByDefault() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        assertNull(transfer.getVersion());
    }
}
