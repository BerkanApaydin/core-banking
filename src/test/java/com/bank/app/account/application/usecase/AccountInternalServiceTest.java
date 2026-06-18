package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.LoadAccountPort;
import com.bank.app.account.application.port.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.security.port.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountInternalServiceTest {

        @Mock
        private LoadAccountPort loadAccountPort;

        @Mock
        private SaveAccountPort saveAccountPort;

        @Mock
        private SecurityContextPort securityContextPort;

        @InjectMocks
        private AccountInternalService accountInternalService;

        @Test
        void shouldThrowExceptionWhenAccountNotFoundInGetAccountInfo() {
                when(loadAccountPort.findById(999L)).thenReturn(Optional.empty());

                AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.getAccountInfo(999L));

                assertTrue(exception.getMessage().contains("999"));
                verify(loadAccountPort).findById(999L);
        }

        @Test
        void shouldThrowExceptionWhenAccountNotFoundInGetAccountInfoForTransfer() {
                when(loadAccountPort.findByIban(any(Iban.class))).thenReturn(Optional.empty());

                AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.getAccountInfoForTransfer("TR290006200000000000000999"));

                assertTrue(exception.getMessage().contains("TR290006200000000000000999"));
                verify(loadAccountPort).findByIban(any(Iban.class));
        }

        @Test
        void shouldReturnEmptyMapWhenAccountIdsIsEmpty() {
                Map<Long, String> result = accountInternalService.getIbansForAccounts(List.of());

                assertTrue(result.isEmpty());
                verifyNoInteractions(loadAccountPort);
        }

        @Test
        void shouldReturnEmptyMapWhenAccountIdsIsNull() {
                Map<Long, String> result = accountInternalService.getIbansForAccounts(null);

                assertTrue(result.isEmpty());
                verifyNoInteractions(loadAccountPort);
        }

        @Test
        void shouldDebitAndCreditWhenSenderIdLessThanReceiverId() {
                Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Sender",
                                new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);
                Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Receiver",
                                new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

                accountInternalService.debitAndCredit(1L, 2L, new Money(new BigDecimal("100.00"), Money.Currency.TRY));

                assertEquals(new BigDecimal("900.00"), sender.getBalance().amount());
                assertEquals(new BigDecimal("600.00"), receiver.getBalance().amount());
                verify(saveAccountPort).save(sender);
                verify(saveAccountPort).save(receiver);
        }

        @Test
        void shouldDebitAndCreditWhenSenderIdGreaterThanReceiverId() {
                Account sender = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Sender",
                                new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);
                Account receiver = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Receiver",
                                new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(sender));
                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(receiver));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(200L), any());

                accountInternalService.debitAndCredit(2L, 1L, new Money(new BigDecimal("100.00"), Money.Currency.TRY));

                assertEquals(new BigDecimal("900.00"), sender.getBalance().amount());
                assertEquals(new BigDecimal("600.00"), receiver.getBalance().amount());
                verify(saveAccountPort).save(sender);
                verify(saveAccountPort).save(receiver);
        }

        @Test
        void shouldThrowExceptionWhenSenderNotFoundInDebitAndCredit() {
                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

                AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.debitAndCredit(1L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));
                assertTrue(ex.getMessage().contains("1"));

                verify(loadAccountPort).findByIdWithLock(1L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenReceiverNotFoundInDebitAndCredit() {
                Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Sender",
                                new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

                AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.debitAndCredit(1L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));
                assertTrue(ex.getMessage().contains("2"));

                verify(loadAccountPort).findByIdWithLock(1L);
                verify(loadAccountPort).findByIdWithLock(2L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldReverseBalancesForCancellationWhenSenderIdLessThanReceiverId() {
                Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Sender",
                                new Money(new BigDecimal("800.00"), Money.Currency.TRY), true);
                Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Receiver",
                                new Money(new BigDecimal("700.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

                accountInternalService.reverseBalancesForCancellation(1L, 2L,
                                new Money(new BigDecimal("200.00"), Money.Currency.TRY));

                assertEquals(new BigDecimal("1000.00"), sender.getBalance().amount());
                assertEquals(new BigDecimal("500.00"), receiver.getBalance().amount());
                verify(saveAccountPort).save(sender);
                verify(saveAccountPort).save(receiver);
        }

        @Test
        void shouldReverseBalancesForCancellationWhenSenderIdGreaterThanReceiverId() {
                Account sender = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Sender",
                                new Money(new BigDecimal("800.00"), Money.Currency.TRY), true);
                Account receiver = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Receiver",
                                new Money(new BigDecimal("700.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(sender));
                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(receiver));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(200L), any());

                accountInternalService.reverseBalancesForCancellation(2L, 1L,
                                new Money(new BigDecimal("200.00"), Money.Currency.TRY));

                assertEquals(new BigDecimal("1000.00"), sender.getBalance().amount());
                assertEquals(new BigDecimal("500.00"), receiver.getBalance().amount());
                verify(saveAccountPort).save(sender);
                verify(saveAccountPort).save(receiver);
        }

        @Test
        void shouldThrowExceptionWhenSenderNotFoundInReverseBalancesForCancellation() {
                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

                assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.reverseBalancesForCancellation(1L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

                verify(loadAccountPort).findByIdWithLock(1L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenReceiverNotFoundInReverseBalancesForCancellation() {
                Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Sender",
                                new Money(new BigDecimal("1000.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

                assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.reverseBalancesForCancellation(1L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

                verify(loadAccountPort).findByIdWithLock(1L);
                verify(loadAccountPort).findByIdWithLock(2L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenReceiverNotFoundInReverseBalancesForCancellationWithSenderGreaterThanReceiver() {
                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

                assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.reverseBalancesForCancellation(3L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

                verify(loadAccountPort).findByIdWithLock(2L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenSenderNotFoundInReverseBalancesForCancellationWithSenderGreaterThanReceiver() {
                Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Receiver",
                                new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
                when(loadAccountPort.findByIdWithLock(3L)).thenReturn(Optional.empty());

                assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.reverseBalancesForCancellation(3L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

                verify(loadAccountPort).findByIdWithLock(2L);
                verify(loadAccountPort).findByIdWithLock(3L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenReceiverNotFoundInDebitAndCreditWithSenderGreaterThanReceiver() {
                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

                assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.debitAndCredit(3L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

                verify(loadAccountPort).findByIdWithLock(2L);
                verify(saveAccountPort, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenSenderNotFoundInDebitAndCreditWithSenderGreaterThanReceiver() {
                Account receiver = new Account(2L, 200L, new Iban("TR290006200000000000000222"), "Receiver",
                                new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

                when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
                when(loadAccountPort.findByIdWithLock(3L)).thenReturn(Optional.empty());

                assertThrows(AccountNotFoundException.class,
                                () -> accountInternalService.debitAndCredit(3L, 2L,
                                                new Money(new BigDecimal("100.00"), Money.Currency.TRY)));

                verify(loadAccountPort).findByIdWithLock(2L);
                verify(loadAccountPort).findByIdWithLock(3L);
                verify(saveAccountPort, never()).save(any());
        }
}
