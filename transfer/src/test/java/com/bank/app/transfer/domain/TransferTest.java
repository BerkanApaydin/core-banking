package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.InvalidAmountException;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import com.bank.app.transfer.domain.exception.TransferNotPendingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.bank.app.common.domain.AccountId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("Transfer domain entity")
class TransferTest {

    private static final Money AMOUNT = Money.of("100.00", Currency.TRY);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);

    private static LocalDateTime now() {
        // UTC like production (time-strategy.md): fixtures and cancel clocks
        // must share one timeline or window-boundary tests skew by the zone offset.
        return LocalDateTime.now(FIXED_CLOCK);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create transfer with PENDING status")
        void shouldCreateWithPendingStatus() {
            Transfer transfer = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(transfer.getCreatedAt()).isNotNull();
            assertThat(transfer.getId()).isNull();
        }

        @Test
        @DisplayName("should create transfer with deterministic clock")
        void shouldCreateWithDeterministicClock() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);

            Transfer transfer = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, clock);

            assertThat(transfer.getCreatedAt())
                    .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        }

        @Test
        @DisplayName("should create transfer with version")
        void shouldCreateWithVersion() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now(), 3L);
            assertThat(transfer.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should create transfer with null version by default")
        void shouldCreateWithNullVersion() {
            Transfer transfer = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            assertThat(transfer.getVersion()).isNull();
        }

        @Test
        @DisplayName("should reject zero amount on create (D-1)")
        void shouldRejectZeroAmountOnCreate() {
            assertThatThrownBy(() -> Transfer.create(new AccountId(1L), new AccountId(2L),
                    Money.of("0.00", Currency.TRY), FIXED_CLOCK))
                    .isExactlyInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("should enforce the single-transfer ceiling on create (D-1)")
        void shouldEnforceCeilingOnCreate() {
            Transfer atCeiling = Transfer.create(new AccountId(1L), new AccountId(2L),
                    Money.of("1000000000.00", Currency.TRY), FIXED_CLOCK);
            assertThat(atCeiling.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThatThrownBy(() -> Transfer.create(new AccountId(1L), new AccountId(2L),
                    Money.of("1000000000.01", Currency.TRY), FIXED_CLOCK))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1000000000.00");
        }

        @ParameterizedTest(name = "should reject null: {0}")
        @ValueSource(strings = { "senderAccountId", "receiverAccountId", "amount", "status", "createdAt" })
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullArgs(String field) {
            assertThatThrownBy(() -> {
                switch (field) {
                    case "senderAccountId" ->
                        Transfer.rehydrate(1L, null, new AccountId(2L), AMOUNT, TransferStatus.COMPLETED, now());
                    case "receiverAccountId" ->
                        Transfer.rehydrate(1L, new AccountId(1L), null, AMOUNT, TransferStatus.COMPLETED, now());
                    case "amount" ->
                        Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), null, TransferStatus.COMPLETED, now());
                    case "status" -> Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, null, now());
                    case "createdAt" ->
                        Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED, null);
                }
            }).isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should return correct getters")
        void shouldReturnCorrectGetters() {
            LocalDateTime now = now();
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(10L), new AccountId(20L), AMOUNT,
                    TransferStatus.COMPLETED, now, 5L);
            assertThat(transfer.getId()).isEqualTo(1L);
            assertThat(transfer.getSenderAccountId()).isEqualTo(new AccountId(10L));
            assertThat(transfer.getReceiverAccountId()).isEqualTo(new AccountId(20L));
            assertThat(transfer.getAmount()).isEqualTo(AMOUNT);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(transfer.getCreatedAt()).isEqualTo(now);
            assertThat(transfer.getVersion()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should reject same sender and receiver in constructor")
        void shouldRejectSameAccountInConstructor() {
            assertThatThrownBy(() -> Transfer.rehydrate(1L, new AccountId(7L), new AccountId(7L), AMOUNT,
                    TransferStatus.COMPLETED, now()))
                    .isExactlyInstanceOf(SameAccountTransferException.class);
        }

        @Test
        @DisplayName("should reject same sender and receiver in create")
        void shouldRejectSameAccountInCreate() {
            assertThatThrownBy(() -> Transfer.create(new AccountId(7L), new AccountId(7L), AMOUNT, FIXED_CLOCK))
                    .isExactlyInstanceOf(SameAccountTransferException.class);
        }

        @Test
        @DisplayName("should reject zero amount for PENDING transfer in constructor")
        void shouldRejectZeroAmountPendingInConstructor() {
            assertThatThrownBy(
                    () -> Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), Money.of("0.00", Currency.TRY),
                            TransferStatus.PENDING, now()))
                    .isExactlyInstanceOf(InvalidAmountException.class);
        }

        @ParameterizedTest(name = "should reject zero amount for {0} transfer in constructor")
        @ValueSource(strings = { "COMPLETED", "FAILED", "CANCELLED" })
        @DisplayName("should reject zero amount regardless of status in constructor")
        void shouldRejectZeroAmountForAnyStatusInConstructor(String status) {
                assertThatThrownBy(
                        () -> Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), Money.of("0.00", Currency.TRY),
                                TransferStatus.valueOf(status), now()))
                        .isExactlyInstanceOf(InvalidAmountException.class)
                        .hasMessageContaining("must not be zero");
        }
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("should complete a PENDING transfer")
        void shouldCompletePending() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());
            transfer.complete(FIXED_CLOCK);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        @DisplayName("should complete with fixed clock and emit event with timestamp")
        void shouldCompleteWithClock() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());

            transfer.complete(clock);

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(transfer.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof TransferCompletedEvent);
            TransferCompletedEvent event = (TransferCompletedEvent) transfer.getDomainEvents().getFirst();
            assertThat(event.occurredAt()).isEqualTo(fixedNow);
        }

        @Test
        @DisplayName("should throw when completing already COMPLETED transfer")
        void shouldThrowOnAlreadyCompleted() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            assertThatThrownBy(() -> transfer.complete(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Only PENDING transfers can be completed. Current status: COMPLETED");
        }

        @Test
        @DisplayName("should throw when completing FAILED transfer")
        void shouldThrowOnFailedStatus() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.FAILED,
                    now());
            assertThatThrownBy(() -> transfer.complete(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Only PENDING transfers can be completed. Current status: FAILED");
        }

        @Test
        @DisplayName("should throw when completing CANCELLED transfer")
        void shouldThrowOnCancelledStatus() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.CANCELLED,
                    now());
            assertThatThrownBy(() -> transfer.complete(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Only PENDING transfers can be completed. Current status: CANCELLED");
        }

        @Test
        @DisplayName("should complete only once")
        void shouldCompleteOnlyOnce() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());
            transfer.complete(FIXED_CLOCK);
            assertThatThrownBy(() -> transfer.complete(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class);
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("should cancel within 24-hour window and register event")
        void shouldCancelWithinWindow() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now().minusHours(2));
            transfer.cancel(FIXED_CLOCK, Duration.ofHours(24));
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
            assertThat(transfer.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof TransferCancelledEvent);
            TransferCancelledEvent event = (TransferCancelledEvent) transfer.getDomainEvents().getFirst();
            assertThat(event.transferId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should cancel at exactly 24-hour window boundary")
        void shouldCancelAtBoundary() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(24 * 3600L), ZoneOffset.UTC);
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    createdAt);
            transfer.cancel(clock, Duration.ofHours(24));
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should allow cancellation with zero window when just created")
        void shouldCancelWithZeroWindow() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    fixedNow);
            transfer.cancel(clock, Duration.ZERO);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw when transfer is older than 24 hours")
        void shouldThrowAfterWindow() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now().minusHours(25));
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ofHours(24)))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("cancellation window has passed");
        }

        @Test
        @DisplayName("should throw just after window boundary")
        void shouldThrowJustAfterBoundary() {
            LocalDateTime createdAt = now().minusHours(24).minusMinutes(1);
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    createdAt);
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ofHours(24)))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("cancellation window has passed");
        }

        @Test
        @DisplayName("should throw when window is zero and transfer is old")
        void shouldThrowWithZeroWindowAndOldTransfer() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now().minusMinutes(1));
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ZERO))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("cancellation window has passed");
        }

        @Test
        @DisplayName("should throw when already cancelled")
        void shouldThrowWhenAlreadyCancelled() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.CANCELLED,
                    now());
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ofHours(24)))
                    .isExactlyInstanceOf(TransferAlreadyCancelledException.class)
                    .hasMessage("Transfer already cancelled. ID: 1");
        }

        @Test
        @DisplayName("should throw when status is FAILED")
        void shouldThrowOnFailedStatus() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.FAILED,
                    now());
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ofHours(24)))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("already failed");
        }

        @Test
        @DisplayName("should throw when status is PENDING")
        void shouldThrowOnPendingStatus() {
            Transfer transfer = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, Duration.ofHours(24)))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("still pending");
        }

        @Test
        @DisplayName("should cancel with deterministic clock within window")
        void shouldCancelWithDeterministicClockWithinWindow() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC);

            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    createdAt);
            transfer.cancel(clock, Duration.ofHours(24));

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should deny cancellation with deterministic clock outside window")
        void shouldDenyWithDeterministicClockOutsideWindow() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(25 * 3600 + 6), ZoneOffset.UTC);

            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    createdAt);
            assertThatThrownBy(() -> transfer.cancel(clock, Duration.ofHours(24)))
                    .isExactlyInstanceOf(TransferNotCancellableException.class);
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("should mark PENDING transfer as FAILED and publish event")
        void shouldMarkFailedFromPending() {
            Instant failedAt = Instant.parse("2026-06-24T12:00:00Z");
            Clock clock = Clock.fixed(failedAt, ZoneOffset.UTC);
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());
            transfer.markFailed(clock);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
            assertThat(transfer.getDomainEvents()).hasSize(1);
            TransferFailedEvent event = (TransferFailedEvent) transfer.getDomainEvents().get(0);
            assertThat(event.transferId()).isEqualTo(1L);
            assertThat(event.status()).isEqualTo(TransferStatus.FAILED);
            assertThat(event.occurredAt())
                    .isEqualTo(LocalDateTime.ofInstant(failedAt, ZoneOffset.UTC));
            assertThat(event.aggregateType()).isEqualTo("Transfer");
            assertThat(event.aggregateId()).isEqualTo("1");
        }

        @Test
        @DisplayName("should mark PENDING transfer as FAILED with fixed clock")
        void shouldMarkFailedFromPendingWithClock() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());
            transfer.markFailed(FIXED_CLOCK);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        }

        @Test
        @DisplayName("should throw when marking already COMPLETED transfer as failed")
        void shouldThrowOnAlreadyCompleted() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            assertThatThrownBy(() -> transfer.markFailed(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Only PENDING transfers can be marked as failed. Current status: COMPLETED");
        }

        @Test
        @DisplayName("should throw when marking already CANCELLED transfer as failed")
        void shouldThrowOnCancelledStatus() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.CANCELLED,
                    now());
            assertThatThrownBy(() -> transfer.markFailed(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Only PENDING transfers can be marked as failed. Current status: CANCELLED");
        }

        @Test
        @DisplayName("should throw when marking already FAILED transfer as failed")
        void shouldThrowOnAlreadyFailed() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.FAILED,
                    now());
            assertThatThrownBy(() -> transfer.markFailed(FIXED_CLOCK))
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Only PENDING transfers can be marked as failed. Current status: FAILED");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true for the same instance")
        void equalsWhenSameInstance() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT,
                    TransferStatus.COMPLETED, now());

            assertThat(transfer).isEqualTo(transfer);
        }

        @Test
        @DisplayName("equals should return true when same ID")
        void equalsWhenSameId() {
            Transfer t1 = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            Transfer t2 = Transfer.rehydrate(1L, new AccountId(3L), new AccountId(4L), Money.of("200.00", Currency.USD),
                    TransferStatus.PENDING, now());
            assertThat(t1).isEqualTo(t2);
        }

        @Test
        @DisplayName("equals should return false when different ID")
        void notEqualsWhenDifferentId() {
            Transfer t1 = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            Transfer t2 = Transfer.rehydrate(2L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("equals should return false when only one ID is null")
        void notEqualsWhenOneNullId() {
            Transfer t1 = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            Transfer t2 = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            assertThat(t1).isNotEqualTo(t2);
            assertThat(t2).isNotEqualTo(t1);
        }

        @Test
        @DisplayName("equals should return false when both IDs are null")
        void notEqualsWhenBothNullIds() {
            Transfer t1 = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            Transfer t2 = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            Transfer t1 = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            assertThat(t1).isNotEqualTo("some-string");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            Transfer t1 = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.COMPLETED,
                    now());
            Transfer t2 = Transfer.rehydrate(1L, new AccountId(3L), new AccountId(4L), Money.of("200.00", Currency.USD),
                    TransferStatus.PENDING, now());
            assertThat(t1).hasSameHashCodeAs(t2);
        }

        @Test
        @DisplayName("hashCode should return 0 when id is null")
        void hashCodeWithNullId() {
            Transfer transfer = Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, FIXED_CLOCK);
            assertThat(transfer.hashCode()).isZero();
        }
    }

    @Nested
    @DisplayName("null contracts")
    class NullContracts {

        @Test
        @DisplayName("should reject null sender in create before same-account check")
        void shouldRejectNullSenderInCreate() {
            assertThatThrownBy(() -> Transfer.create(null, null, AMOUNT, FIXED_CLOCK))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Sender account ID must not be null");
        }

        @Test
        @DisplayName("should reject null receiver in create")
        void shouldRejectNullReceiverInCreate() {
            assertThatThrownBy(() -> Transfer.create(new AccountId(1L), null, AMOUNT, FIXED_CLOCK))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Receiver account ID must not be null");
        }

        @Test
        @DisplayName("should reject null amount in create")
        void shouldRejectNullAmountInCreate() {
            assertThatThrownBy(() -> Transfer.create(new AccountId(1L), new AccountId(2L), null, FIXED_CLOCK))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Transfer amount must not be null");
        }

        @Test
        @DisplayName("should reject null clock in create")
        void shouldRejectNullClockInCreate() {
            assertThatThrownBy(() -> Transfer.create(new AccountId(1L), new AccountId(2L), AMOUNT, null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Clock must not be null");
        }

        @Test
        @DisplayName("should reject null clock in complete")
        void shouldRejectNullClockInComplete() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());
            assertThatThrownBy(() -> transfer.complete(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Clock must not be null");
        }

        @Test
        @DisplayName("should reject null clock in cancel before status checks")
        void shouldRejectNullClockInCancel() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.CANCELLED,
                    now());
            assertThatThrownBy(() -> transfer.cancel(null, Duration.ofHours(24)))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Clock must not be null");
        }

        @Test
        @DisplayName("should reject null window in cancel before status checks")
        void shouldRejectNullWindowInCancel() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.CANCELLED,
                    now());
            assertThatThrownBy(() -> transfer.cancel(FIXED_CLOCK, null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Cancellation window must not be null");
        }

        @Test
        @DisplayName("should reject null clock in markFailed")
        void shouldRejectNullClockInMarkFailed() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(1L), new AccountId(2L), AMOUNT, TransferStatus.PENDING,
                    now());
            assertThatThrownBy(() -> transfer.markFailed(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Clock must not be null");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include id, sender, receiver, amount, and status")
        void shouldIncludeFields() {
            Transfer transfer = Transfer.rehydrate(1L, new AccountId(10L), new AccountId(20L), AMOUNT,
                    TransferStatus.COMPLETED, now());
            assertThat(transfer.toString())
                    .contains("id=1")
                    .contains("sender=10")
                    .contains("receiver=20")
                    .contains("status=COMPLETED");
        }
    }
}
