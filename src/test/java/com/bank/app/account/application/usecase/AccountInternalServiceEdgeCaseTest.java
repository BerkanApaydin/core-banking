package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.LoadAccountPort;
import com.bank.app.account.application.port.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.AccountNotActiveException;
import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.exception.InsufficientBalanceException;
import com.bank.app.common.security.port.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountInternalServiceEdgeCaseTest {

    @Mock private LoadAccountPort loadAccountPort;
    @Mock private SaveAccountPort saveAccountPort;
    @Mock private SecurityContextPort securityContextPort;

    @InjectMocks
    private AccountInternalService accountInternalService;

    @Test
    void shouldHandleDebitAndCreditOnSameAccount() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000111"),
                "Ahmet", new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(account));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        accountInternalService.debitAndCredit(1L, 1L,
                new Money(new BigDecimal("100.00"), Money.Currency.TRY));

        assertEquals(new BigDecimal("1000.00"), account.getBalance().amount());

        verify(saveAccountPort, times(2)).save(any());
    }

    @Test
    void shouldThrowInsufficientBalanceOnReverseCancellationWhenReceiverCannotPayBack() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"),
                "Sender", new Money(new BigDecimal("800.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"),
                "Receiver", new Money(new BigDecimal("50.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        assertThrows(InsufficientBalanceException.class,
                () -> accountInternalService.reverseBalancesForCancellation(1L, 2L,
                        new Money(new BigDecimal("100.00"), Money.Currency.TRY)));
    }

    @Test
    void shouldThrowAccountNotActiveOnReverseCancellationWhenReceiverIsInactive() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"),
                "Sender", new Money(new BigDecimal("800.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"),
                "Receiver", new Money(new BigDecimal("500.00"), Money.Currency.TRY), false);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        assertThrows(AccountNotActiveException.class,
                () -> accountInternalService.reverseBalancesForCancellation(1L, 2L,
                        new Money(new BigDecimal("100.00"), Money.Currency.TRY)));
    }

    @Test
    void shouldDebitAndCreditWithZeroAmount() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"),
                "Sender", new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"),
                "Receiver", new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        accountInternalService.debitAndCredit(1L, 2L,
                new Money(new BigDecimal("0.00"), Money.Currency.TRY));

        assertEquals(new BigDecimal("1000.00"), sender.getBalance().amount());
        assertEquals(new BigDecimal("500.00"), receiver.getBalance().amount());
    }

    @Test
    void shouldThrowWhenAccountNotFoundOnGetAccountInfoForTransferWithInvalidIban() {
        when(loadAccountPort.findByIban(any(Iban.class))).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> accountInternalService.getAccountInfoForTransfer("TR290006200000000000000999"));

        verify(loadAccountPort).findByIban(any(Iban.class));
    }

    @Test
    void shouldThrowAccessDeniedOnDebitAndCreditWhenUnauthorized() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"),
                "Sender", new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"),
                "Receiver", new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doThrow(new AccessDeniedException("Unauthorized"))
                .when(securityContextPort).checkUserAuthorization(eq(100L), any());

        assertThrows(AccessDeniedException.class,
                () -> accountInternalService.debitAndCredit(1L, 2L,
                        new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

        verify(saveAccountPort, never()).save(any());
    }

    @Test
    void shouldThrowAccessDeniedOnReverseCancellationWhenUnauthorized() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"),
                "Sender", new Money(new BigDecimal("800.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"),
                "Receiver", new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doThrow(new AccessDeniedException("Unauthorized"))
                .when(securityContextPort).checkUserAuthorization(eq(100L), any());

        assertThrows(AccessDeniedException.class,
                () -> accountInternalService.reverseBalancesForCancellation(1L, 2L,
                        new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

        verify(saveAccountPort, never()).save(any());
    }

    @Test
    void shouldReturnEmptyMapWhenGetIbansForAccountsWithNullIds() {
        assertTrue(accountInternalService.getIbansForAccounts(null).isEmpty());
        verifyNoInteractions(loadAccountPort);
    }

    @Test
    void shouldReturnEmptyMapWhenGetIbansForAccountsWithEmptyIds() {
        assertTrue(accountInternalService.getIbansForAccounts(java.util.List.of()).isEmpty());
        verifyNoInteractions(loadAccountPort);
    }
}
