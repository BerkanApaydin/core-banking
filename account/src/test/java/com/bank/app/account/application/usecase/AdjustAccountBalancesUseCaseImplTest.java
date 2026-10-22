package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.port.out.SaveLedgerPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.LedgerDirection;
import com.bank.app.account.domain.LedgerEntry;
import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.domain.exception.DistinctAccountsRequiredException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private IdGeneratorPort idGenerator;

    @Mock
    private DomainEventPublisherService domainEventPublisherService;

    @Mock
    private AuditEventPort auditEventPort;

    @Mock
    private SaveLedgerPort ledgerPort;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @Captor
    private ArgumentCaptor<AuditEvent> auditEventCaptor;

    @Captor
    private ArgumentCaptor<LedgerEntry> ledgerCaptor;

    private AdjustAccountBalancesUseCaseImpl useCase;
    private Account senderAccount;
    private Account receiverAccount;

    private static final String TEST_IBAN_1 = "TR450006100519786456841234";
    private static final String TEST_IBAN_2 = "TR180006100519786456841235";

    @BeforeEach
    void setUp() {
        useCase = new AdjustAccountBalancesUseCaseImpl(loadAccountPort, saveAccountPort, clockProvider, idGenerator,
                domainEventPublisherService, auditEventPort, ledgerPort);
        senderAccount = new Account(1L, new UserId(10L), new Iban(TEST_IBAN_1),
                "Sender", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        receiverAccount = new Account(2L, new UserId(20L), new Iban(TEST_IBAN_2),
                "Receiver", Money.of("500.00", Currency.TRY), AccountStatus.ACTIVE);
    }

    private void stubClockAndSave() {
        when(clockProvider.clock()).thenReturn(
                java.time.Clock.fixed(java.time.Instant.parse("2026-06-22T10:00:00Z"),
                        java.time.ZoneOffset.UTC));
        // D-1: seeded ref — one move() consumes exactly one ID.
        when(idGenerator.newId()).thenReturn("test-operation-ref");
        // 7.2: the adapter echoes the saved aggregate (with bumped version);
        // the use case must keep using the returned instances.
        when(saveAccountPort.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("debitAndCredit")
    class DebitAndCredit {
        @Test
        void shouldDebitSenderAndCreditReceiver() {
            stubClockAndSave();
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
            verify(domainEventPublisherService, times(2)).publishEvents(any(Account.class));
            // One audit row per balance leg, in the same transaction.
            verify(auditEventPort, times(2)).publish(auditEventCaptor.capture());
            var auditEvents = auditEventCaptor.getAllValues();
            assertEquals("ACCOUNT_DEBITED", auditEvents.get(0).action());
            assertTrue(auditEvents.get(0).details().contains("1"));
            // Kills the NegateConditionals mutant on the verb derivation:
            // a negated mutant swaps Debited<->Credited in the human message.
            assertTrue(auditEvents.get(0).details().contains("Debited"));
            assertEquals("ACCOUNT_CREDITED", auditEvents.get(1).action());
            assertTrue(auditEvents.get(1).details().contains("2"));
            assertTrue(auditEvents.get(1).details().contains("Credited"));
            // Double-entry journal: both legs share one ref and net to zero.
            verify(ledgerPort, times(2)).save(ledgerCaptor.capture());
            var legs = ledgerCaptor.getAllValues();
            assertEquals(LedgerDirection.DEBIT, legs.get(0).getDirection());
            assertEquals(1L, legs.get(0).getAccountId());
            assertEquals(Money.of("200.00", Currency.TRY), legs.get(0).getAmount());
            assertEquals(Money.of("800.00", Currency.TRY), legs.get(0).getBalanceAfter());
            assertEquals(LedgerDirection.CREDIT, legs.get(1).getDirection());
            assertEquals(2L, legs.get(1).getAccountId());
            assertEquals(Money.of("700.00", Currency.TRY), legs.get(1).getBalanceAfter());
            assertEquals(legs.get(0).getTransactionRef(), legs.get(1).getTransactionRef());
        }

        @Test
        void shouldRejectSameAccount() {
            Money amount = Money.of("200.00", Currency.TRY);

            DistinctAccountsRequiredException ex = assertThrows(DistinctAccountsRequiredException.class,
                    () -> useCase.debitAndCredit(1L, 1L, amount));
            assertEquals("DISTINCT_ACCOUNTS_REQUIRED", ex.getErrorCode());
            verifyNoInteractions(loadAccountPort, saveAccountPort, domainEventPublisherService, auditEventPort,
                    ledgerPort);
        }

        @Test
        void shouldThrowWhenAccountNotFound() {
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> useCase.debitAndCredit(1L, 99L, Money.of("10.00", Currency.TRY)));
            verify(saveAccountPort, never()).save(any(Account.class));
            verify(clockProvider, never()).clock();
            verify(domainEventPublisherService, never()).publishEvents(any(Account.class));
            verify(auditEventPort, never()).publish(any(AuditEvent.class));
            verify(ledgerPort, never()).save(any(LedgerEntry.class));
        }

        @Test
        void shouldThrowWhenSecondAccountNotFound() {
            // Kills the NullReturnVals mutant on the second lock supplier:
            // the receiver leg must also fail fast when its row is missing.
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> useCase.debitAndCredit(1L, 99L, Money.of("10.00", Currency.TRY)));
            verify(saveAccountPort, never()).save(any(Account.class));
            verify(clockProvider, never()).clock();
            verify(domainEventPublisherService, never()).publishEvents(any(Account.class));
            verify(auditEventPort, never()).publish(any(AuditEvent.class));
            verify(ledgerPort, never()).save(any(LedgerEntry.class));
        }

        @Test
        void shouldThrowWhenSenderIdIsNull() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> useCase.debitAndCredit(null, 2L, Money.of("10.00", Currency.TRY)));
            assertEquals("id1 must not be null", ex.getMessage());
            verifyNoInteractions(saveAccountPort, domainEventPublisherService, auditEventPort,
                    ledgerPort);
        }

        @Test
        void shouldThrowWhenReceiverIdIsNull() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> useCase.debitAndCredit(1L, null, Money.of("10.00", Currency.TRY)));
            assertEquals("id2 must not be null", ex.getMessage());
            verifyNoInteractions(saveAccountPort, domainEventPublisherService, auditEventPort,
                    ledgerPort);
        }

        @Test
        void shouldRejectNullAmountBeforeTouchingAnyPort() {
            // A null amount must fail fast at the boundary: loading and
            // locking accounts for a movement that cannot happen would only
            // hold row locks for nothing.
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> useCase.debitAndCredit(1L, 2L, null));
            assertEquals("Amount must not be null", ex.getMessage());
            verifyNoInteractions(loadAccountPort, saveAccountPort, domainEventPublisherService, auditEventPort,
                    ledgerPort);
        }
    }

    @Nested
    @DisplayName("reverseForCancellation")
    class ReverseBalances {
        @Test
        void shouldCreditSenderAndDebitReceiver() {
            stubClockAndSave();
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
            verify(domainEventPublisherService, times(2)).publishEvents(any(Account.class));
            // Reversal mirrors the legs: sender credited, receiver debited.
            verify(auditEventPort, times(2)).publish(auditEventCaptor.capture());
            var auditEvents = auditEventCaptor.getAllValues();
            assertEquals("ACCOUNT_CREDITED", auditEvents.get(0).action());
            assertTrue(auditEvents.get(0).details().contains("Credited"));
            assertEquals("ACCOUNT_DEBITED", auditEvents.get(1).action());
            assertTrue(auditEvents.get(1).details().contains("Debited"));
            verify(ledgerPort, times(2)).save(ledgerCaptor.capture());
            var legs = ledgerCaptor.getAllValues();
            assertEquals(LedgerDirection.CREDIT, legs.get(0).getDirection());
            assertEquals(1L, legs.get(0).getAccountId());
            assertEquals(LedgerDirection.DEBIT, legs.get(1).getDirection());
            assertEquals(2L, legs.get(1).getAccountId());
            assertEquals(legs.get(0).getTransactionRef(), legs.get(1).getTransactionRef());
        }

        @Test
        void shouldResolvePartiesWhenSenderIdIsGreaterThanReceiverId() {
            stubClockAndSave();
            Money amount = Money.of("200.00", Currency.TRY);
            when(loadAccountPort.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));

            AccountAdjustmentResult result = useCase.debitAndCredit(2L, 1L, amount);

            assertEquals(2L, result.senderAccountId());
            assertEquals(1L, result.receiverAccountId());
            assertEquals(Money.of("300.00", Currency.TRY), result.senderNewBalance());
            assertEquals(Money.of("1200.00", Currency.TRY), result.receiverNewBalance());
            verify(saveAccountPort, times(2)).save(any(Account.class));
            verify(domainEventPublisherService, times(2)).publishEvents(any(Account.class));
        }

        @Test
        void shouldRejectSameAccount() {
            Money amount = Money.of("200.00", Currency.TRY);

            assertThrows(DistinctAccountsRequiredException.class,
                    () -> useCase.reverseForCancellation(2L, 2L, amount));
            verifyNoInteractions(loadAccountPort, saveAccountPort, domainEventPublisherService, auditEventPort,
                    ledgerPort);
        }

        @Test
        void shouldRejectNullAmountBeforeTouchingAnyPort() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> useCase.reverseForCancellation(1L, 2L, null));
            assertEquals("Amount must not be null", ex.getMessage());
            verifyNoInteractions(loadAccountPort, saveAccountPort, domainEventPublisherService, auditEventPort,
                    ledgerPort);
        }

        @Test
        void shouldFailWhenReceiverCannotCoverTheReversal() {
            // The cancellation reversal debits the receiver. When the receiver
            // no longer holds the money the compensation must fail loudly
            // instead of reporting a successful reversal.
            when(clockProvider.clock()).thenReturn(
                    java.time.Clock.fixed(java.time.Instant.parse("2026-06-22T10:00:00Z"),
                            java.time.ZoneOffset.UTC));
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

            InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                    () -> useCase.reverseForCancellation(1L, 2L, Money.of("600.00", Currency.TRY)));

            assertEquals("INSUFFICIENT_BALANCE", ex.getErrorCode());
            verify(saveAccountPort, never()).save(any(Account.class));
            verify(ledgerPort, never()).save(any(LedgerEntry.class));
            verify(auditEventPort, never()).publish(any(AuditEvent.class));
            verify(domainEventPublisherService, never()).publishEvents(any(Account.class));
        }

        @Test
        void shouldFailWhenReceiverIsSuspended() {
            Account suspendedReceiver = new Account(2L, new UserId(20L), new Iban(TEST_IBAN_2),
                    "Receiver", Money.of("500.00", Currency.TRY), AccountStatus.SUSPENDED);
            when(clockProvider.clock()).thenReturn(
                    java.time.Clock.fixed(java.time.Instant.parse("2026-06-22T10:00:00Z"),
                            java.time.ZoneOffset.UTC));
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
            when(loadAccountPort.findByIdForUpdate(2L)).thenReturn(Optional.of(suspendedReceiver));

            AccountNotActiveException ex = assertThrows(AccountNotActiveException.class,
                    () -> useCase.reverseForCancellation(1L, 2L, Money.of("100.00", Currency.TRY)));

            assertEquals("ACCOUNT_NOT_ACTIVE", ex.getErrorCode());
            verify(saveAccountPort, never()).save(any(Account.class));
            verify(ledgerPort, never()).save(any(LedgerEntry.class));
            verify(auditEventPort, never()).publish(any(AuditEvent.class));
            verify(domainEventPublisherService, never()).publishEvents(any(Account.class));
        }
    }
}
