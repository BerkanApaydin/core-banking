package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;

import com.bank.app.common.exception.SameAccountTransferException;
import com.bank.app.common.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferDomainServiceTest {

    private final TransferDomainService transferDomainService = new TransferDomainService();

    @Test
    void shouldCreateTransferSuccessfully() {
        Transfer transfer = transferDomainService.execute(
                1L, "TR290006200000000000000111", Money.Currency.TRY,
                2L, "TR290006200000000000000222", Money.Currency.TRY,
                Money.of("300.00", Money.Currency.TRY));

        assertNotNull(transfer);
        assertEquals(1L, transfer.getSenderAccountId());
        assertEquals(2L, transfer.getReceiverAccountId());
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
    }

    @Test
    void shouldThrowSameAccountTransferExceptionWhenIdsAreEqual() {
        assertThrows(SameAccountTransferException.class, () ->
                transferDomainService.execute(1L, "TR1", Money.Currency.TRY,
                        1L, "TR2", Money.Currency.TRY,
                        Money.of("100.00", Money.Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenArgsAreNull() {
        assertThrows(NullPointerException.class, () ->
                transferDomainService.execute(null, "TR1", Money.Currency.TRY,
                        2L, "TR2", Money.Currency.TRY,
                        Money.of("100.00", Money.Currency.TRY)));
        assertThrows(NullPointerException.class, () ->
                transferDomainService.execute(1L, null, Money.Currency.TRY,
                        2L, "TR2", Money.Currency.TRY,
                        Money.of("100.00", Money.Currency.TRY)));
        assertThrows(NullPointerException.class, () ->
                transferDomainService.execute(1L, "TR1", Money.Currency.TRY,
                        2L, "TR2", Money.Currency.TRY, null));
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionWhenSenderCurrencyMismatchesAmountCurrency() {
        assertThrows(CurrencyMismatchException.class, () ->
                transferDomainService.execute(1L, "TR1", Money.Currency.USD,
                        2L, "TR2", Money.Currency.TRY,
                        Money.of("100.00", Money.Currency.TRY)));
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionWhenReceiverCurrencyMismatchesAmountCurrency() {
        assertThrows(CurrencyMismatchException.class, () ->
                transferDomainService.execute(1L, "TR1", Money.Currency.TRY,
                        2L, "TR2", Money.Currency.USD,
                        Money.of("100.00", Money.Currency.TRY)));
    }
}
