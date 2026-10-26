package com.bank.app.transfer.domain;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("Transfer mutator event registration")
class TransferMutatorOrderingTest {

    private static final Money AMOUNT = Money.of("100.00", Currency.TRY);
    private static final AccountId SENDER = new AccountId(1L);
    private static final AccountId RECEIVER = new AccountId(2L);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);

    private static LocalDateTime now() {
        return LocalDateTime.now(FIXED_CLOCK);
    }

    @Test
    @DisplayName("should register the completion event when the aggregate carries an identifier")
    void shouldRegisterCompletionEventWhenAggregateHasIdentifier() {
        Transfer transfer = Transfer.rehydrate(7L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.PENDING, now());

        transfer.complete(FIXED_CLOCK);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getDomainEvents())
                .hasSize(1)
                .allMatch(event -> event instanceof TransferCompletedEvent);
    }

    @Test
    @DisplayName("should register the cancellation event when the aggregate carries an identifier")
    void shouldRegisterCancellationEventWhenAggregateHasIdentifier() {
        Transfer transfer = Transfer.rehydrate(7L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, now().minusHours(1));

        transfer.cancel(FIXED_CLOCK, Duration.ofHours(24));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(transfer.getDomainEvents())
                .hasSize(1)
                .allMatch(event -> event instanceof TransferCancelledEvent);
    }

    @Test
    @DisplayName("should register the failure event when the aggregate carries an identifier")
    void shouldRegisterFailureEventWhenAggregateHasIdentifier() {
        Transfer transfer = Transfer.rehydrate(7L, SENDER, RECEIVER, AMOUNT,
                TransferStatus.PENDING, now());

        transfer.markFailed(FIXED_CLOCK);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getDomainEvents())
                .hasSize(1)
                .allMatch(event -> event instanceof TransferFailedEvent);
    }

    @Test
    @DisplayName("should not mark a transfer COMPLETED when the completion event cannot be built")
    void shouldNotFlipStatusWhenCompletionEventCannotBeBuilt() {
        // Transient aggregate (no id yet): the event constructor rejects it.
        // Flipping the status first left a COMPLETED row with no
        // TransferCompletedEvent, so no notification and no audit row.
        Transfer transfer = Transfer.rehydrate(null, SENDER, RECEIVER, AMOUNT,
                TransferStatus.PENDING, now());

        assertThatThrownBy(() -> transfer.complete(FIXED_CLOCK))
                .isInstanceOf(NullPointerException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("should not mark a transfer FAILED when the failure event cannot be built")
    void shouldNotFlipStatusWhenFailureEventCannotBeBuilt() {
        Transfer transfer = Transfer.rehydrate(null, SENDER, RECEIVER, AMOUNT,
                TransferStatus.PENDING, now());

        assertThatThrownBy(() -> transfer.markFailed(FIXED_CLOCK))
                .isInstanceOf(NullPointerException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("should not mark a transfer CANCELLED when the cancellation event cannot be built")
    void shouldNotFlipStatusWhenCancellationEventCannotBeBuilt() {
        Transfer transfer = Transfer.rehydrate(null, SENDER, RECEIVER, AMOUNT,
                TransferStatus.COMPLETED, now().minusHours(1));

        assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ofHours(24)))
                .isInstanceOf(NullPointerException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getDomainEvents()).isEmpty();
    }
}
