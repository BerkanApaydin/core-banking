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
        
        transfer.cancel();
        
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldThrowTransferAlreadyCancelledExceptionWhenAlreadyCancelled() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());
        
        assertThrows(TransferAlreadyCancelledException.class, transfer::cancel);
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenStatusIsNotCompleted() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.FAILED, LocalDateTime.now());
        
        assertThrows(TransferNotCancellableException.class, transfer::cancel);
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenOlderThan24Hours() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(25));
        
        assertThrows(TransferNotCancellableException.class, transfer::cancel);
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
}

