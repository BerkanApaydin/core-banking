package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.exception.SameAccountTransferException;
import com.bank.app.common.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransferDomainServiceTest {

    private final TransferDomainService transferDomainService = new TransferDomainService();

    @Test
    void shouldExecuteTransferSuccessfully() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Money.Currency.TRY), true);
        Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Mehmet", Money.of("500.00", Money.Currency.TRY), true);
        Money amount = Money.of("300.00", Money.Currency.TRY);

        Transfer transfer = transferDomainService.execute(sender, receiver, amount);

        assertNotNull(transfer);
        assertEquals(1L, transfer.getSenderAccountId());
        assertEquals(2L, transfer.getReceiverAccountId());
        assertEquals(new BigDecimal("300.00"), transfer.getAmount().amount());
        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());

        assertEquals(new BigDecimal("700.00"), sender.getBalance().amount());
        assertEquals(new BigDecimal("800.00"), receiver.getBalance().amount());
    }

    @Test
    void shouldThrowSameAccountTransferExceptionWhenIbansAreEqual() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Money.Currency.TRY), true);
        Money amount = Money.of("100.00", Money.Currency.TRY);

        assertThrows(SameAccountTransferException.class, () ->
                transferDomainService.execute(account, account, amount));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenArgsAreNull() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Money.Currency.TRY), true);
        Money amount = Money.of("100.00", Money.Currency.TRY);

        assertThrows(NullPointerException.class, () ->
                transferDomainService.execute(null, account, amount));
        assertThrows(NullPointerException.class, () ->
                transferDomainService.execute(account, null, amount));
        assertThrows(NullPointerException.class, () ->
                transferDomainService.execute(account, account, null));
    }

    @Test
    void shouldThrowSameAccountTransferExceptionWhenIdsAreEqualButIbansAreDifferent() {
        assertThrows(SameAccountTransferException.class, () ->
                transferDomainService.execute(1L, "TR1", Money.Currency.TRY,
                        1L, "TR2", Money.Currency.TRY,
                        Money.of("100.00", Money.Currency.TRY)));
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

