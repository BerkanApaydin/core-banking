package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;

import com.bank.app.common.exception.TransferAlreadyCancelledException;
import com.bank.app.common.exception.TransferNotCancellableException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransferTest {

    @Test
    void shouldCancelTransferSuccessfullyWhenWithin24HoursAndCompleted() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(2));
        
        transfer.cancel(24);
        
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldThrowTransferAlreadyCancelledExceptionWhenAlreadyCancelled() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());
        
        TransferAlreadyCancelledException ex = assertThrows(TransferAlreadyCancelledException.class,
                () -> transfer.cancel(24));
        assertEquals("Transfer zaten iptal edilmiş. ID: 1", ex.getMessage());
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenStatusIsNotCompleted() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.FAILED, LocalDateTime.now());

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("Sadece tamamlanmış transferler iptal edilebilir"));
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenOlderThan24Hours() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(25));

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenConstructorArgsAreNull() {
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, null, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, null, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, 2L, null, TransferStatus.COMPLETED, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), null, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, null));
    }

    @Test
    void shouldCompletePendingTransferSuccessfully() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Money.Currency.TRY));
        assertEquals(TransferStatus.PENDING, transfer.getStatus());

        transfer.complete();

        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenCompletingNonPendingTransfer() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());

        assertThrows(IllegalStateException.class, transfer::complete);
    }

    @Test
    void shouldCreateTransferWithVersion() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.PENDING, LocalDateTime.now(), 3L);
        assertEquals(3L, transfer.getVersion());
    }

    @Test
    void shouldCreateTransferWithNullVersionByDefault() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Money.Currency.TRY));
        assertNull(transfer.getVersion());
    }

    @Test
    void shouldCompleteAndFailStatusTransitionsCorrectly() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Money.Currency.TRY));
        assertEquals(TransferStatus.PENDING, transfer.getStatus());

        transfer.complete();
        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());

        assertThrows(IllegalStateException.class, transfer::complete);
    }
}

