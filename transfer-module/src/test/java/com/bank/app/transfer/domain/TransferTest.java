package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;

import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TransferTest {

    @Test
    void shouldCancelTransferSuccessfullyWhenWithin24HoursAndCompleted() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(2));
        
        transfer.cancel(24);
        
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldThrowTransferAlreadyCancelledExceptionWhenAlreadyCancelled() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());
        
        TransferAlreadyCancelledException ex = assertThrows(TransferAlreadyCancelledException.class,
                () -> transfer.cancel(24));
        assertEquals("Transfer zaten iptal edilmiş. ID: 1", ex.getMessage());
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenStatusIsFailed() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.FAILED, LocalDateTime.now());

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertEquals("Sadece tamamlanmış transferler iptal edilebilir. Mevcut durum: FAILED", ex.getMessage());
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenStatusIsPending() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("Sadece tamamlanmış transferler iptal edilebilir"));
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenOlderThan24Hours() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(25));

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenConstructorArgsAreNull() {
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, null, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, null, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, 2L, null, TransferStatus.COMPLETED, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), null, LocalDateTime.now()));
        assertThrows(NullPointerException.class, () ->
                new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, null));
    }

    @Test
    void shouldCompletePendingTransferSuccessfully() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        assertEquals(TransferStatus.PENDING, transfer.getStatus());

        transfer.complete();

        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenCompletingNonPendingTransfer() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());

        assertThrows(IllegalStateException.class, transfer::complete);
    }

    @Test
    void shouldCreateTransferWithVersion() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.PENDING, LocalDateTime.now(), 3L);
        assertEquals(3L, transfer.getVersion());
    }

    @Test
    void shouldCreateTransferWithNullVersionByDefault() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        assertNull(transfer.getVersion());
    }

    @Test
    void shouldCompleteAndFailStatusTransitionsCorrectly() {
        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        assertEquals(TransferStatus.PENDING, transfer.getStatus());

        transfer.complete();
        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());

        assertThrows(IllegalStateException.class, transfer::complete);
    }

    @Test
    void equalsShouldReturnTrueWhenSameId() {
        Transfer t1 = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        Transfer t2 = new Transfer(1L, 3L, 4L, Money.of("200.00", Currency.USD), TransferStatus.PENDING, LocalDateTime.now());
        assertEquals(t1, t2);
    }

    @Test
    void equalsShouldReturnFalseWhenDifferentId() {
        Transfer t1 = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        Transfer t2 = new Transfer(2L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        assertNotEquals(t1, t2);
    }

    @Test
    void equalsShouldReturnFalseWhenBothNullIds() {
        Transfer t1 = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        Transfer t2 = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY));
        assertNotEquals(t1, t2);
    }

    @Test
    void equalsShouldReturnFalseWhenOtherObject() {
        Transfer t1 = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        assertNotEquals("some-string", t1);
    }

    @Test
    void hashCodeShouldBeConsistentWithEquals() {
        Transfer t1 = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        Transfer t2 = new Transfer(1L, 3L, 4L, Money.of("200.00", Currency.USD), TransferStatus.PENDING, LocalDateTime.now());
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void shouldCancelWhenWindowIsZeroAndWithinGracePeriod() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        transfer.cancel(0);
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldThrowWhenWindowIsExpiredByNegativeWindow() {
        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusMinutes(1));
        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(0));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));
    }

    @Test
    void shouldReturnCorrectGetters() {
        LocalDateTime now = LocalDateTime.now();
        Transfer transfer = new Transfer(1L, 10L, 20L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, now, 5L);
        assertEquals(10L, transfer.getSenderAccountId());
        assertEquals(20L, transfer.getReceiverAccountId());
        assertEquals(now, transfer.getCreatedAt());
        assertEquals(5L, transfer.getVersion());
    }

    @Test
    void shouldCancelWithDeterministicClockWithinWindow() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.systemDefault());
        LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(1), ZoneId.systemDefault());

        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, createdAt);
        transfer.cancel(24, clock);

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldDenyCancellationWithDeterministicClockOutsideWindow() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.systemDefault());
        LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(25 * 3600 + 6), ZoneId.systemDefault());

        Transfer transfer = new Transfer(1L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, createdAt);
        assertThrows(TransferNotCancellableException.class,
                () -> transfer.cancel(24, clock));
    }

    @Test
    void shouldCreateTransferWithDeterministicClock() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.systemDefault());

        Transfer transfer = Transfer.create(1L, 2L, Money.of("100.00", Currency.TRY), clock);

        assertEquals(LocalDateTime.ofInstant(now, ZoneId.systemDefault()), transfer.getCreatedAt());
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
    }
}

