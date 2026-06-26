package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import com.bank.app.transfer.domain.exception.TransferNotPendingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("Transfer domain entity")
class TransferTest {

    private static final Money AMOUNT = Money.of("100.00", Currency.TRY);

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should create transfer with PENDING status")
        void shouldCreateWithPendingStatus() {
            Transfer transfer = Transfer.create(1L, 2L, AMOUNT);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(transfer.getCreatedAt()).isNotNull();
            assertThat(transfer.getId()).isNull();
        }

        @Test
        @DisplayName("should create transfer with deterministic clock")
        void shouldCreateWithDeterministicClock() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneId.systemDefault());

            Transfer transfer = Transfer.create(1L, 2L, AMOUNT, clock);

            assertThat(transfer.getCreatedAt())
                    .isEqualTo(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        }

        @Test
        @DisplayName("should create transfer with version")
        void shouldCreateWithVersion() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.PENDING, now(), 3L);
            assertThat(transfer.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should create transfer with null version by default")
        void shouldCreateWithNullVersion() {
            Transfer transfer = Transfer.create(1L, 2L, AMOUNT);
            assertThat(transfer.getVersion()).isNull();
        }

        @ParameterizedTest(name = "should reject null: {0}")
        @ValueSource(strings = {"senderAccountId", "receiverAccountId", "amount", "status", "createdAt"})
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullArgs(String field) {
            assertThatThrownBy(() -> {
                switch (field) {
                    case "senderAccountId" -> new Transfer(1L, null, 2L, AMOUNT, TransferStatus.COMPLETED, now());
                    case "receiverAccountId" -> new Transfer(1L, 1L, null, AMOUNT, TransferStatus.COMPLETED, now());
                    case "amount" -> new Transfer(1L, 1L, 2L, null, TransferStatus.COMPLETED, now());
                    case "status" -> new Transfer(1L, 1L, 2L, AMOUNT, null, now());
                    case "createdAt" -> new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, null);
                }
            }).isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should return correct getters")
        void shouldReturnCorrectGetters() {
            LocalDateTime now = now();
            Transfer transfer = new Transfer(1L, 10L, 20L, AMOUNT, TransferStatus.COMPLETED, now, 5L);
            assertThat(transfer.getSenderAccountId()).isEqualTo(10L);
            assertThat(transfer.getReceiverAccountId()).isEqualTo(20L);
            assertThat(transfer.getCreatedAt()).isEqualTo(now);
            assertThat(transfer.getVersion()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("should complete a PENDING transfer")
        void shouldCompletePending() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.PENDING, now());
            transfer.complete();
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        @DisplayName("should throw when completing already COMPLETED transfer")
        void shouldThrowOnAlreadyCompleted() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            assertThatThrownBy(transfer::complete)
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: COMPLETED");
        }

        @Test
        @DisplayName("should throw when completing FAILED transfer")
        void shouldThrowOnFailedStatus() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.FAILED, now());
            assertThatThrownBy(transfer::complete)
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: FAILED");
        }

        @Test
        @DisplayName("should throw when completing CANCELLED transfer")
        void shouldThrowOnCancelledStatus() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.CANCELLED, now());
            assertThatThrownBy(transfer::complete)
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: CANCELLED");
        }

        @Test
        @DisplayName("should complete only once")
        void shouldCompleteOnlyOnce() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.PENDING, now());
            transfer.complete();
            assertThatThrownBy(transfer::complete)
                    .isExactlyInstanceOf(TransferNotPendingException.class);
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("should cancel within 24-hour window")
        void shouldCancelWithinWindow() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now().minusHours(2));
            transfer.cancel(Clock.systemDefaultZone(), 24);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should cancel at exactly 24-hour window boundary")
        void shouldCancelAtBoundary() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            transfer.cancel(Clock.systemDefaultZone(), 24);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should allow cancellation with zero window when just created")
        void shouldCancelWithZeroWindow() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, fixedNow);
            transfer.cancel(clock, 0);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw when transfer is older than 24 hours")
        void shouldThrowAfterWindow() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now().minusHours(25));
            assertThatThrownBy(() -> transfer.cancel(Clock.systemDefaultZone(), 24))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("saat geçtiği için iptal edilemez");
        }

        @Test
        @DisplayName("should throw just after window boundary")
        void shouldThrowJustAfterBoundary() {
            LocalDateTime createdAt = now().minusHours(24).minusMinutes(1);
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, createdAt);
            assertThatThrownBy(() -> transfer.cancel(Clock.systemDefaultZone(), 24))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("saat geçtiği için iptal edilemez");
        }

        @Test
        @DisplayName("should throw when window is zero and transfer is old")
        void shouldThrowWithZeroWindowAndOldTransfer() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now().minusMinutes(1));
            assertThatThrownBy(() -> transfer.cancel(Clock.systemDefaultZone(), 0))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("saat geçtiği için iptal edilemez");
        }

        @Test
        @DisplayName("should throw when already cancelled")
        void shouldThrowWhenAlreadyCancelled() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.CANCELLED, now());
            assertThatThrownBy(() -> transfer.cancel(Clock.systemDefaultZone(), 24))
                    .isExactlyInstanceOf(TransferAlreadyCancelledException.class)
                    .hasMessage("Transfer zaten iptal edilmiş. ID: 1");
        }

        @Test
        @DisplayName("should throw when status is FAILED")
        void shouldThrowOnFailedStatus() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.FAILED, now());
            assertThatThrownBy(() -> transfer.cancel(Clock.systemDefaultZone(), 24))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("Sadece tamamlanmış transferler iptal edilebilir");
        }

        @Test
        @DisplayName("should throw when status is PENDING")
        void shouldThrowOnPendingStatus() {
            Transfer transfer = Transfer.create(1L, 2L, AMOUNT);
            assertThatThrownBy(() -> transfer.cancel(Clock.systemDefaultZone(), 24))
                    .isExactlyInstanceOf(TransferNotCancellableException.class)
                    .hasMessageContaining("Sadece tamamlanmış transferler iptal edilebilir");
        }

        @Test
        @DisplayName("should cancel with deterministic clock within window")
        void shouldCancelWithDeterministicClockWithinWindow() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneId.systemDefault());
            LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(1), ZoneId.systemDefault());

            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, createdAt);
            transfer.cancel(clock, 24);

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("should deny cancellation with deterministic clock outside window")
        void shouldDenyWithDeterministicClockOutsideWindow() {
            Instant now = Instant.parse("2026-06-22T10:00:00Z");
            Clock clock = Clock.fixed(now, ZoneId.systemDefault());
            LocalDateTime createdAt = LocalDateTime.ofInstant(now.minusSeconds(25 * 3600 + 6), ZoneId.systemDefault());

            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, createdAt);
            assertThatThrownBy(() -> transfer.cancel(clock, 24))
                    .isExactlyInstanceOf(TransferNotCancellableException.class);
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("should mark PENDING transfer as FAILED")
        void shouldMarkFailedFromPending() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.PENDING, now());
            transfer.markFailed();
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        }

        @Test
        @DisplayName("should mark PENDING transfer as FAILED with fixed clock")
        void shouldMarkFailedFromPendingWithClock() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.PENDING, now());
            transfer.markFailed(Clock.systemDefaultZone());
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        }

        @Test
        @DisplayName("should throw when marking already COMPLETED transfer as failed")
        void shouldThrowOnAlreadyCompleted() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            assertThatThrownBy(transfer::markFailed)
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: COMPLETED");
        }

        @Test
        @DisplayName("should throw when marking already CANCELLED transfer as failed")
        void shouldThrowOnCancelledStatus() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.CANCELLED, now());
            assertThatThrownBy(transfer::markFailed)
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: CANCELLED");
        }

        @Test
        @DisplayName("should throw when marking already FAILED transfer as failed")
        void shouldThrowOnAlreadyFailed() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.FAILED, now());
            assertThatThrownBy(transfer::markFailed)
                    .isExactlyInstanceOf(TransferNotPendingException.class)
                    .hasMessage("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: FAILED");
        }
    }

    @Nested
    @DisplayName("cancel no-arg")
    class CancelNoArg {

        @Test
        @DisplayName("should cancel using default 24-hour window")
        void shouldCancelWithDefaultWindow() {
            Transfer transfer = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now().minusHours(2));
            transfer.cancel();
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true when same ID")
        void equalsWhenSameId() {
            Transfer t1 = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            Transfer t2 = new Transfer(1L, 3L, 4L, Money.of("200.00", Currency.USD), TransferStatus.PENDING, now());
            assertThat(t1).isEqualTo(t2);
        }

        @Test
        @DisplayName("equals should return false when different ID")
        void notEqualsWhenDifferentId() {
            Transfer t1 = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            Transfer t2 = new Transfer(2L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("equals should return false when both IDs are null")
        void notEqualsWhenBothNullIds() {
            Transfer t1 = Transfer.create(1L, 2L, AMOUNT);
            Transfer t2 = Transfer.create(1L, 2L, AMOUNT);
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            Transfer t1 = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            assertThat(t1).isNotEqualTo("some-string");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            Transfer t1 = new Transfer(1L, 1L, 2L, AMOUNT, TransferStatus.COMPLETED, now());
            Transfer t2 = new Transfer(1L, 3L, 4L, Money.of("200.00", Currency.USD), TransferStatus.PENDING, now());
            assertThat(t1).hasSameHashCodeAs(t2);
        }
    }
}
