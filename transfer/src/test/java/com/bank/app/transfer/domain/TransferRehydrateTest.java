package com.bank.app.transfer.domain;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.InvalidAmountException;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Transfer#rehydrate} is the production rehydration entry point
 * (TransferJpaMapper.toDomain, TransferJpaMapper.java:38). A row read from
 * the database must become an aggregate with exactly the persisted state and
 * without re-emitting history: a rehydrated aggregate that registers events
 * would duplicate notifications on every read.
 */
@SuppressWarnings("null")
@DisplayName("Transfer.rehydrate")
class TransferRehydrateTest {

    private static final Money AMOUNT = Money.of("250.00", Currency.TRY);
    private static final AccountId SENDER = new AccountId(10L);
    private static final AccountId RECEIVER = new AccountId(20L);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 22, 10, 0);

    @Test
    @DisplayName("should rebuild the aggregate state from a persisted row")
    void shouldRebuildAggregateStateFromPersistedRow() {
        Transfer transfer = Transfer.rehydrate(42L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, CREATED_AT, 7L);

        assertThat(transfer.getId()).isEqualTo(42L);
        assertThat(transfer.getSenderAccountId()).isEqualTo(SENDER);
        assertThat(transfer.getReceiverAccountId()).isEqualTo(RECEIVER);
        assertThat(transfer.getAmount()).isEqualTo(AMOUNT);
        assertThat(transfer.getAmount().amount()).isEqualByComparingTo("250.00");
        assertThat(transfer.getAmount().currency()).isEqualTo(Currency.TRY);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(transfer.getVersion()).isEqualTo(7L);
    }

    @Test
    @DisplayName("should not register any domain event when rehydrating")
    void shouldNotRegisterDomainEventsWhenRehydrating() {
        Transfer transfer = Transfer.rehydrate(42L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, CREATED_AT, 7L);

        // Re-emitting the status transition on every read would publish a
        // duplicate COMPLETED notification / audit row per read.
        assertThat(transfer.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("should reject a persisted row with a zero amount")
    void shouldRejectPersistedRowWithZeroAmount() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, SENDER, RECEIVER,
                Money.of("0.00", Currency.TRY), TransferStatus.COMPLETED, CREATED_AT, 7L))
                .isExactlyInstanceOf(InvalidAmountException.class)
                .hasMessage("Transfer amount must not be zero");
    }

    @Test
    @DisplayName("should reject a persisted row whose sender and receiver are the same account")
    void shouldRejectPersistedRowWithSameSenderAndReceiver() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, new AccountId(5L), new AccountId(5L),
                AMOUNT, TransferStatus.COMPLETED, CREATED_AT, 7L))
                .isExactlyInstanceOf(SameAccountTransferException.class);
    }

    @Test
    @DisplayName("should never materialize a negative amount")
    void shouldNeverMaterializeNegativeAmount() {
        // Money itself refuses a negative amount, so a corrupt row with a
        // negative amount fails in the mapper (Money.exact) and can never
        // reach the aggregate — there is no negative transfer to cancel.
        assertThatThrownBy(() -> Money.exact(new BigDecimal("-1.00"), Currency.TRY))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must not be negative");
    }

    @Test
    @DisplayName("should accept a completed row whose amount does not match the status")
    void shouldAcceptCompletedRowWithSmallAmount() {
        // The domain validates the amount range, not a status/amount
        // combination: a COMPLETED row with a cent-sized amount is legal and
        // must be readable (a rejection here would make the row unreadable
        // and the transfer uncancellable).
        Transfer transfer = Transfer.rehydrate(42L, SENDER, RECEIVER,
                Money.of("0.01", Currency.TRY), TransferStatus.COMPLETED, CREATED_AT, 7L);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getAmount().amount()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("should accept a pending row for every persisted status")
    void shouldAcceptEveryPersistedStatus() {
        for (TransferStatus status : TransferStatus.values()) {
            Transfer transfer = Transfer.rehydrate(42L, SENDER, RECEIVER, AMOUNT,
                    status, CREATED_AT, 7L);
            assertThat(transfer.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("should reject a persisted row without a status")
    void shouldRejectPersistedRowWithoutStatus() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, SENDER, RECEIVER, AMOUNT,
                null, CREATED_AT, 7L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Status must not be null");
    }

    @Test
    @DisplayName("should reject a persisted row without an amount")
    void shouldRejectPersistedRowWithoutAmount() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, SENDER, RECEIVER,
                null, TransferStatus.COMPLETED, CREATED_AT, 7L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Transfer amount must not be null");
    }

    @Test
    @DisplayName("should reject a persisted row without a sender account")
    void shouldRejectPersistedRowWithoutSender() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, null, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, CREATED_AT, 7L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Sender account ID must not be null");
    }

    @Test
    @DisplayName("should reject a persisted row without a receiver account")
    void shouldRejectPersistedRowWithoutReceiver() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, SENDER, null, AMOUNT,
                TransferStatus.COMPLETED, CREATED_AT, 7L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Receiver account ID must not be null");
    }

    @Test
    @DisplayName("should reject a persisted row without a creation timestamp")
    void shouldRejectPersistedRowWithoutCreatedAt() {
        assertThatThrownBy(() -> Transfer.rehydrate(42L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, null, 7L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Created date must not be null");
    }

    @Test
    @DisplayName("should accept a persisted row without a version")
    void shouldAcceptPersistedRowWithoutVersion() {
        Transfer transfer = Transfer.rehydrate(42L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, CREATED_AT, null);

        assertThat(transfer.getVersion()).isNull();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }
}
