package com.bank.app.account.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
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

import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteTransferUseCaseImpl")
class ExecuteTransferUseCaseImplTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SaveAccountPort saveAccountPort;

    @Mock
    private SecurityContextPort securityContextPort;

    @Mock
    private EventPublisherPort eventPublisherPort;

    private ExecuteTransferUseCaseImpl useCase;

    private Account sender;
    private Account receiver;

    @Captor
    private ArgumentCaptor<Account> senderCaptor;

    @Captor
    private ArgumentCaptor<Account> receiverCaptor;

    @BeforeEach
    void setUp() {
        useCase = new ExecuteTransferUseCaseImpl(loadAccountPort, saveAccountPort,
                securityContextPort, eventPublisherPort);

        sender = Account.builder()
                .id(1L).userId(new UserId(10L))
                .iban(new Iban("TR111111111111111111111111"))
                .ownerName("Alice")
                .balance(Money.of("1000.00", Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .build();

        receiver = Account.builder()
                .id(2L).userId(new UserId(20L))
                .iban(new Iban("TR222222222222222222222222"))
                .ownerName("Bob")
                .balance(Money.of("500.00", Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("should debit sender and credit receiver successfully")
        void shouldTransferSuccessfully() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
            Money amount = Money.of("200.00", Currency.TRY);

            useCase.execute(1L, 2L, amount);

            verify(loadAccountPort).findByIdWithLock(1L);
            verify(loadAccountPort).findByIdWithLock(2L);
            verify(securityContextPort).checkUserAuthorization(10L, "Bu hesaptan transfer yapmaya yetkiniz yok.");
            assertEquals(Money.of("800.00", Currency.TRY), sender.getBalance());
            assertEquals(Money.of("700.00", Currency.TRY), receiver.getBalance());
            verify(saveAccountPort).save(sender);
            verify(saveAccountPort).save(receiver);
            verify(eventPublisherPort, times(3)).publish(any());
        }

        @Test
        @DisplayName("should handle sender.id < receiver.id for deadlock-safe ordering")
        void shouldHandleDeadlockSafeOrdering() {
            sender = Account.builder()
                    .id(1L).userId(new UserId(10L))
                    .iban(new Iban("TR111111111111111111111111"))
                    .ownerName("Alice")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .build();
            receiver = Account.builder()
                    .id(3L).userId(new UserId(20L))
                    .iban(new Iban("TR222222222222222222222222"))
                    .ownerName("Bob")
                    .balance(Money.of("500.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(3L)).thenReturn(Optional.of(receiver));
            Money amount = Money.of("100.00", Currency.TRY);

            useCase.execute(1L, 3L, amount);

            InOrder order = inOrder(loadAccountPort);
            order.verify(loadAccountPort).findByIdWithLock(1L);
            order.verify(loadAccountPort).findByIdWithLock(3L);
        }

        @Test
        @DisplayName("should handle receiver.id < sender.id for deadlock-safe ordering")
        void shouldHandleReverseOrder() {
            sender = Account.builder()
                    .id(5L).userId(new UserId(10L))
                    .iban(new Iban("TR111111111111111111111111"))
                    .ownerName("Alice")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .build();
            receiver = Account.builder()
                    .id(4L).userId(new UserId(20L))
                    .iban(new Iban("TR222222222222222222222222"))
                    .ownerName("Bob")
                    .balance(Money.of("500.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(loadAccountPort.findByIdWithLock(4L)).thenReturn(Optional.of(receiver));
            when(loadAccountPort.findByIdWithLock(5L)).thenReturn(Optional.of(sender));
            Money amount = Money.of("100.00", Currency.TRY);

            useCase.execute(5L, 4L, amount);

            InOrder order = inOrder(loadAccountPort);
            order.verify(loadAccountPort).findByIdWithLock(4L);
            order.verify(loadAccountPort).findByIdWithLock(5L);
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when sender not found")
        void shouldThrowWhenSenderNotFound() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> useCase.execute(1L, 2L, Money.of("100.00", Currency.TRY)));
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when receiver not found")
        void shouldThrowWhenReceiverNotFound() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> useCase.execute(1L, 2L, Money.of("100.00", Currency.TRY)));
        }

        @Test
        @DisplayName("should throw AccountNotActiveException when sender is suspended")
        void shouldThrowWhenSenderNotActive() {
            sender = Account.builder()
                    .id(1L).userId(new UserId(10L))
                    .iban(new Iban("TR111111111111111111111111"))
                    .ownerName("Alice")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.SUSPENDED)
                    .build();
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));

            assertThrows(AccountNotActiveException.class,
                    () -> useCase.execute(1L, 2L, Money.of("100.00", Currency.TRY)));
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when sender balance is insufficient")
        void shouldThrowWhenInsufficientBalance() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));

            assertThrows(InsufficientBalanceException.class,
                    () -> useCase.execute(1L, 2L, Money.of("99999.00", Currency.TRY)));
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
        @DisplayName("should clean domain events after publishing")
        void shouldClearDomainEventsAfterPublishing() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
            Money amount = Money.of("200.00", Currency.TRY);

            useCase.execute(1L, 2L, amount);

            assertTrue(sender.getDomainEvents().isEmpty());
            assertTrue(receiver.getDomainEvents().isEmpty());
        }
    }
}
