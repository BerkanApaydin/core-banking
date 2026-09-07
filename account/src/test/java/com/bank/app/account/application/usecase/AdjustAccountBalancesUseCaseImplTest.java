package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
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

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdjustAccountBalancesUseCaseImpl")
@SuppressWarnings("null")
class AdjustAccountBalancesUseCaseImplTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SaveAccountPort saveAccountPort;

    @Mock
    private ClockProviderPort clockProvider;

    @Mock
    private DomainEventPublisherService domainEventPublisherService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private AdjustAccountBalancesUseCaseImpl useCase;
    private Account senderAccount;
    private Account receiverAccount;

    private static final String TEST_IBAN_1 = "TR330006100519786456841234";
    private static final String TEST_IBAN_2 = "TR660006100519786456841235";

    @BeforeEach
    void setUp() {
        lenient().when(clockProvider.clock()).thenReturn(Clock.systemDefaultZone());
        useCase = new AdjustAccountBalancesUseCaseImpl(loadAccountPort, saveAccountPort, clockProvider,
                domainEventPublisherService);
        senderAccount = new Account(1L, new UserId(10L), new Iban(TEST_IBAN_1),
                "Sender", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        receiverAccount = new Account(2L, new UserId(20L), new Iban(TEST_IBAN_2),
                "Receiver", Money.of("500.00", Currency.TRY), AccountStatus.ACTIVE);
    }

    @Nested
    @DisplayName("debitAndCredit")
    class DebitAndCredit {
        @Test
        void shouldDebitSenderAndCreditReceiver() {
            Money amount = Money.of("200.00", Currency.TRY);
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

            AccountAdjustmentResult result = useCase.debitAndCredit(1L, 2L, amount);

            verify(saveAccountPort, times(2)).save(accountCaptor.capture());
            var savedAccounts = accountCaptor.getAllValues();
            var savedSender = savedAccounts.stream().filter(a -> a.getId().equals(1L)).findFirst().orElseThrow();
            var savedReceiver = savedAccounts.stream().filter(a -> a.getId().equals(2L)).findFirst().orElseThrow();
            assertEquals(Money.of("800.00", Currency.TRY), savedSender.getBalance());
            assertEquals(Money.of("700.00", Currency.TRY), savedReceiver.getBalance());
            assertEquals(1L, result.senderAccountId());
            assertEquals(2L, result.receiverAccountId());
            assertEquals(Money.of("800.00", Currency.TRY), result.senderNewBalance());
            assertEquals(Money.of("700.00", Currency.TRY), result.receiverNewBalance());
            // Account publishes its own domain events; they never leak to the caller.
            verify(domainEventPublisherService, times(2)).publish(any(DomainEvent.class));
        }

        @Test
        void shouldRejectSameAccount() {
            Money amount = Money.of("200.00", Currency.TRY);

            assertThrows(IllegalArgumentException.class, () -> useCase.debitAndCredit(1L, 1L, amount));
            verifyNoInteractions(loadAccountPort, saveAccountPort, domainEventPublisherService);
        }

        @Test
        void shouldThrowWhenAccountNotFound() {
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> useCase.debitAndCredit(1L, 99L, Money.of("10.00", Currency.TRY)));
            verify(domainEventPublisherService, never()).publish(any(DomainEvent.class));
        }
    }

    @Nested
    @DisplayName("reverseForCancellation")
    class ReverseBalances {
        @Test
        void shouldCreditSenderAndDebitReceiver() {
            Money amount = Money.of("200.00", Currency.TRY);
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

            AccountAdjustmentResult result = useCase.reverseForCancellation(1L, 2L, amount);

            verify(saveAccountPort, times(2)).save(accountCaptor.capture());
            var savedAccounts = accountCaptor.getAllValues();
            var savedSender = savedAccounts.stream().filter(a -> a.getId().equals(1L)).findFirst().orElseThrow();
            var savedReceiver = savedAccounts.stream().filter(a -> a.getId().equals(2L)).findFirst().orElseThrow();
            assertEquals(Money.of("1200.00", Currency.TRY), savedSender.getBalance());
            assertEquals(Money.of("300.00", Currency.TRY), savedReceiver.getBalance());
            assertEquals(Money.of("1200.00", Currency.TRY), result.senderNewBalance());
            assertEquals(Money.of("300.00", Currency.TRY), result.receiverNewBalance());
            verify(domainEventPublisherService, times(2)).publish(any(DomainEvent.class));
        }

        @Test
        void shouldRejectSameAccount() {
            Money amount = Money.of("200.00", Currency.TRY);

            assertThrows(IllegalArgumentException.class, () -> useCase.reverseForCancellation(2L, 2L, amount));
            verifyNoInteractions(loadAccountPort, saveAccountPort, domainEventPublisherService);
        }
    }
}
