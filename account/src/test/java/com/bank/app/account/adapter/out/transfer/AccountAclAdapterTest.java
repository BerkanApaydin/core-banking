package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountAclAdapter")
class AccountAclAdapterTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SaveAccountPort saveAccountPort;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private AccountAclAdapter adapter;
    private Account senderAccount;
    private Account receiverAccount;

    private static final String TEST_IBAN_1 = "TR330006100519786456841234";
    private static final String TEST_IBAN_2 = "TR660006100519786456841235";

    @BeforeEach
    void setUp() {
        adapter = new AccountAclAdapter(loadAccountPort, saveAccountPort);
        senderAccount = new Account(1L, new UserId(10L), new Iban(TEST_IBAN_1),
                "Sender", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        receiverAccount = new Account(2L, new UserId(20L), new Iban(TEST_IBAN_2),
                "Receiver", Money.of("500.00", Currency.TRY), AccountStatus.ACTIVE);
    }

    @Nested
    @DisplayName("getAccountInfo")
    class GetAccountInfo {
        @Test
        void shouldThrowWhenAccountNotFound() {
            when(loadAccountPort.findById(99L)).thenReturn(Optional.empty());
            assertThrows(com.bank.app.account.domain.exception.AccountNotFoundException.class,
                    () -> adapter.getAccountInfo(99L));
        }

        @Test
        void shouldReturnAccountInfoWhenAccountExists() {
            when(loadAccountPort.findById(1L)).thenReturn(Optional.of(senderAccount));

            AccountAclPort.AccountInfo result = adapter.getAccountInfo(1L);

            assertEquals(1L, result.id());
            assertEquals(10L, result.userId());
            assertEquals("TRY", result.currency());
            assertEquals("ACTIVE", result.status());
        }
    }

    @Nested
    @DisplayName("getAccountInfoForTransfer")
    class GetAccountInfoForTransfer {
        @Test
        void shouldReturnAccountInfoForValidIban() {
            when(loadAccountPort.findByIban(new Iban(TEST_IBAN_1))).thenReturn(Optional.of(senderAccount));

            AccountAclPort.AccountInfo result = adapter.getAccountInfoForTransfer(TEST_IBAN_1);

            assertEquals(1L, result.id());
            assertEquals("TRY", result.currency());
        }
    }

    @Nested
    @DisplayName("getIbansForAccounts")
    class GetIbansForAccounts {
        @Test
        void shouldReturnMapOfIdsToIbans() {
            when(loadAccountPort.findByIds(Set.of(1L, 2L)))
                    .thenReturn(java.util.List.of(senderAccount, receiverAccount));

            var result = adapter.getIbansForAccounts(Set.of(1L, 2L));

            assertEquals(TEST_IBAN_1, result.get(1L));
            assertEquals(TEST_IBAN_2, result.get(2L));
        }

        @Test
        void shouldReturnEmptyMapForNullIds() {
            var result = adapter.getIbansForAccounts(null);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("debitAndCredit")
    class DebitAndCredit {
        @Test
        void shouldDebitSenderAndCreditReceiver() {
            Money amount = Money.of("200.00", Currency.TRY);
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiverAccount));

            List<DomainEvent> events = adapter.debitAndCredit(1L, 2L, amount);

            verify(saveAccountPort, times(2)).save(accountCaptor.capture());
            var savedAccounts = accountCaptor.getAllValues();
            var savedSender = savedAccounts.stream().filter(a -> a.getId().equals(1L)).findFirst().orElseThrow();
            var savedReceiver = savedAccounts.stream().filter(a -> a.getId().equals(2L)).findFirst().orElseThrow();
            assertEquals(Money.of("800.00", Currency.TRY), savedSender.getBalance());
            assertEquals(Money.of("700.00", Currency.TRY), savedReceiver.getBalance());
            assertEquals(2, events.size());
        }
    }

    @Nested
    @DisplayName("reverseBalancesForCancellation")
    class ReverseBalances {
        @Test
        void shouldCreditSenderAndDebitReceiver() {
            Money amount = Money.of("200.00", Currency.TRY);
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiverAccount));

            List<DomainEvent> events = adapter.reverseBalancesForCancellation(1L, 2L, amount);

            verify(saveAccountPort, times(2)).save(accountCaptor.capture());
            var savedAccounts = accountCaptor.getAllValues();
            var savedSender = savedAccounts.stream().filter(a -> a.getId().equals(1L)).findFirst().orElseThrow();
            var savedReceiver = savedAccounts.stream().filter(a -> a.getId().equals(2L)).findFirst().orElseThrow();
            assertEquals(Money.of("1200.00", Currency.TRY), savedSender.getBalance());
            assertEquals(Money.of("300.00", Currency.TRY), savedReceiver.getBalance());
            assertEquals(2, events.size());
        }
    }
}
