package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferAccountHelper")
class TransferAccountHelperTest {

    private static final Iban SENDER_IBAN = new Iban("TR111111111111111111111111");
    private static final Iban RECEIVER_IBAN = new Iban("TR222222222222222222222222");

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SaveAccountPort saveAccountPort;

    @Mock
    private DomainEventPublisherService domainEventPublisherService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            com.bank.app.common.domain.event.DomainEventProvider p = invocation.getArgument(0);
            p.clearDomainEvents();
            return null;
        }).when(domainEventPublisherService).publishEvents(any());
    }

    private Account createAccount(Long id, Long userId, Iban iban, Money balance) {
        return new Account(id, new UserId(userId), iban, "Owner", balance, AccountStatus.ACTIVE);
    }

    @Nested
    @DisplayName("loadOrderedPair")
    class LoadOrderedPair {

        @Test
        @DisplayName("should return ordered pair with lower id first")
        void shouldOrderLowerIdFirst() {
            Account acc1 = createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            Account acc2 = createAccount(2L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));

            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(acc1));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(acc2));

            OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(1L, 2L, loadAccountPort);

            assertThat(pair.lowerIdItem().getId()).isEqualTo(1L);
            assertThat(pair.higherIdItem().getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should return ordered pair when ids are reversed")
        void shouldOrderWhenReversed() {
            Account acc1 = createAccount(5L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            Account acc2 = createAccount(3L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));

            when(loadAccountPort.findByIdWithLock(3L)).thenReturn(Optional.of(acc2));
            when(loadAccountPort.findByIdWithLock(5L)).thenReturn(Optional.of(acc1));

            OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(5L, 3L, loadAccountPort);

            assertThat(pair.lowerIdItem().getId()).isEqualTo(3L);
            assertThat(pair.higherIdItem().getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should throw when first account not found")
        void shouldThrowWhenFirstNotFound() {
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> TransferAccountHelper.loadOrderedPair(1L, 2L, loadAccountPort))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when second account not found")
        void shouldThrowWhenSecondNotFound() {
            Account acc1 = createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(acc1));
            when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> TransferAccountHelper.loadOrderedPair(1L, 2L, loadAccountPort))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw on null id1")
        void shouldThrowOnNullId1() {
            assertThatThrownBy(() -> TransferAccountHelper.loadOrderedPair(null, 2L, loadAccountPort))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw on null id2")
        void shouldThrowOnNullId2() {
            assertThatThrownBy(() -> TransferAccountHelper.loadOrderedPair(1L, null, loadAccountPort))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("resolveSender")
    class ResolveSender {

        @Test
        @DisplayName("should return lower id item when senderId < receiverId")
        void shouldReturnLowerWhenSenderFirst() {
            Account lower = createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            Account higher = createAccount(2L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));
            OrderedPair<Account> pair = new OrderedPair<>(lower, higher);

            Account result = TransferAccountHelper.resolveSender(pair, 1L, 2L);

            assertThat(result).isSameAs(lower);
        }

        @Test
        @DisplayName("should return higher id item when senderId > receiverId")
        void shouldReturnHigherWhenSenderSecond() {
            Account lower = createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            Account higher = createAccount(5L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));
            OrderedPair<Account> pair = new OrderedPair<>(lower, higher);

            Account result = TransferAccountHelper.resolveSender(pair, 5L, 1L);

            assertThat(result).isSameAs(higher);
        }
    }

    @Nested
    @DisplayName("resolveReceiver")
    class ResolveReceiver {

        @Test
        @DisplayName("should return higher id item when senderId < receiverId")
        void shouldReturnHigherWhenSenderFirst() {
            Account lower = createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            Account higher = createAccount(2L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));
            OrderedPair<Account> pair = new OrderedPair<>(lower, higher);

            Account result = TransferAccountHelper.resolveReceiver(pair, 1L, 2L);

            assertThat(result).isSameAs(higher);
        }

        @Test
        @DisplayName("should return lower id item when senderId > receiverId")
        void shouldReturnLowerWhenSenderSecond() {
            Account lower = createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY));
            Account higher = createAccount(5L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));
            OrderedPair<Account> pair = new OrderedPair<>(lower, higher);

            Account result = TransferAccountHelper.resolveReceiver(pair, 5L, 1L);

            assertThat(result).isSameAs(lower);
        }
    }

    @Nested
    @DisplayName("saveAndPublishEvents")
    class SaveAndPublishEvents {

        private Account triggerEvent(Account account) {
            account.debit(Money.of("100", Currency.TRY));
            return account;
        }

        @Test
        @DisplayName("should save both accounts and publish events")
        void shouldSaveBothAndPublish() {
            Account sender = triggerEvent(createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY)));
            Account receiver = createAccount(2L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY));

            TransferAccountHelper.saveAndPublishEvents(sender, receiver, saveAccountPort, domainEventPublisherService);

            verify(saveAccountPort).save(sender);
            verify(saveAccountPort).save(receiver);
            verify(domainEventPublisherService, times(2)).publishEvents(any());
        }

        @Test
        @DisplayName("should clear domain events after publishing")
        void shouldClearDomainEvents() {
            Account sender = triggerEvent(createAccount(1L, 10L, SENDER_IBAN, Money.of("1000", Currency.TRY)));
            Account receiver = triggerEvent(createAccount(2L, 20L, RECEIVER_IBAN, Money.of("500", Currency.TRY)));

            TransferAccountHelper.saveAndPublishEvents(sender, receiver, saveAccountPort, domainEventPublisherService);

            assertThat(sender.getDomainEvents()).isEmpty();
            assertThat(receiver.getDomainEvents()).isEmpty();
        }
    }

    @Test
    @DisplayName("should have accessible constructor")
    void shouldHaveAccessibleConstructor() {
        TransferAccountHelper helper = new TransferAccountHelper();
        assertThat(helper).isNotNull();
    }
}
