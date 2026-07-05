package com.bank.app.account.application.usecase;

import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReverseTransferUseCaseImpl")
class ReverseTransferUseCaseImplTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SaveAccountPort saveAccountPort;

    @Mock
    private UserContextService userContextService;

    @Mock
    private DomainEventPublisherService domainEventPublisherService;

    @Mock
    private AuditEventPort auditEventPort;

    private ReverseTransferUseCaseImpl useCase;

    private Account sender;
    private Account receiver;

    @BeforeEach
    void setUp() {
        AccountAuthorizationService accountAuthorizationService = new AccountAuthorizationService(userContextService);
        useCase = new ReverseTransferUseCaseImpl(loadAccountPort, saveAccountPort,
                accountAuthorizationService, domainEventPublisherService, auditEventPort);

        lenient().doAnswer(invocation -> {
            com.bank.app.common.domain.event.DomainEventProvider p = invocation.getArgument(0);
            p.clearDomainEvents();
            return null;
        }).when(domainEventPublisherService).publishEvents(any());

        sender = Account.builder()
                .id(1L).userId(new UserId(10L))
                .iban(new Iban("TR111111111111111111111111"))
                .ownerName("Alice")
                .balance(Money.of("800.00", Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .build();

        receiver = Account.builder()
                .id(2L).userId(new UserId(20L))
                .iban(new Iban("TR222222222222222222222222"))
                .ownerName("Bob")
                .balance(Money.of("700.00", Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("should credit sender and debit receiver successfully")
        void shouldReverseSuccessfully() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
            Money amount = Money.of("200.00", Currency.TRY);

            useCase.execute(1L, 2L, amount);

            verify(loadAccountPort).findByIdWithLock(1L);
            verify(loadAccountPort).findByIdWithLock(2L);
            verify(userContextService).checkUserAuthorization(10L, "You are not authorized for this operation.");
            assertThat(sender.getBalance()).isEqualTo(Money.of("1000.00", Currency.TRY));
            assertThat(receiver.getBalance()).isEqualTo(Money.of("500.00", Currency.TRY));
            verify(saveAccountPort).save(sender);
            verify(saveAccountPort).save(receiver);
            verify(domainEventPublisherService, times(2)).publishEvents(any());
            verify(auditEventPort).publish(any());
        }

        @Test
        @DisplayName("should throw when sender account not found")
        void shouldThrowWhenSenderNotFound() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(1L, 2L, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when receiver account not found")
        void shouldThrowWhenReceiverNotFound() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(1L, 2L, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when senderId is null")
        void shouldThrowOnNullSenderId() {
            assertThatThrownBy(() -> useCase.execute(null, 2L, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when receiverId is null")
        void shouldThrowOnNullReceiverId() {
            assertThatThrownBy(() -> useCase.execute(1L, null, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when amount is null")
        void shouldThrowOnNullAmount() {
            assertThatThrownBy(() -> useCase.execute(1L, 2L, null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should handle deadlock-safe ordering when senderId < receiverId")
        void shouldHandleDeadlockSafeOrdering() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));

            useCase.execute(1L, 2L, Money.of("200.00", Currency.TRY));

            InOrder order = inOrder(loadAccountPort);
            order.verify(loadAccountPort).findByIdWithLock(1L);
            order.verify(loadAccountPort).findByIdWithLock(2L);
        }

        @Test
        @DisplayName("should handle deadlock-safe ordering when receiverId < senderId")
        void shouldHandleDeadlockSafeOrderingReverse() {
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
            when(loadAccountPort.findByIdWithLock(5L)).thenReturn(Optional.of(sender));

            useCase.execute(5L, 2L, Money.of("200.00", Currency.TRY));

            InOrder order = inOrder(loadAccountPort);
            order.verify(loadAccountPort).findByIdWithLock(2L);
            order.verify(loadAccountPort).findByIdWithLock(5L);
        }

        @Test
        @DisplayName("should throw when receiver not found in deadlock-safe else branch")
        void shouldThrowWhenReceiverNotFoundWithReversedOrder() {
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(5L, 2L, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when sender not found in deadlock-safe else branch")
        void shouldThrowWhenSenderNotFoundWithReversedOrder() {
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
            when(loadAccountPort.findByIdWithLock(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(5L, 2L, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should clean domain events after publishing")
        void shouldClearDomainEventsAfterPublishing() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
            Money amount = Money.of("200.00", Currency.TRY);

            useCase.execute(1L, 2L, amount);

            assertThat(sender.getDomainEvents()).isEmpty();
            assertThat(receiver.getDomainEvents()).isEmpty();
        }
    }
}
