package com.bank.app.account.domain;

import com.bank.app.account.domain.exception.AccountClosedException;
import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("Account domain entity")
class AccountTest {

    private static final Iban IBAN = new Iban("TR290006200000000000000111");
    private static final String OWNER = "Ahmet Yılmaz";

    private Account activeAccount(long balance) {
        return new Account(1L, new UserId(1L), IBAN, OWNER, Money.of(String.valueOf(balance), Currency.TRY),
                AccountStatus.ACTIVE);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullArgs() {
            assertThatThrownBy(() -> new Account(1L, new UserId(1L), null, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, new UserId(1L), IBAN, null, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, new UserId(1L), IBAN, OWNER, null, AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                    () -> new Account(1L, null, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY), null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest(name = "should reject blank owner name: \"{0}\"")
        @ValueSource(strings = { "", "   " })
        @DisplayName("should reject blank owner names")
        void shouldRejectBlankOwnerName(String name) {
            assertThatThrownBy(() -> new Account(1L, new UserId(1L), IBAN, name, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should create account with null ID")
        void shouldCreateWithNullId() {
            Account account = new Account(null, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(account.getId()).isNull();
        }

        @Test
        @DisplayName("should create account with version")
        void shouldCreateWithVersion() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE, 5L);
            assertThat(account.getVersion()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should create account with null version by default")
        void shouldCreateWithNullVersion() {
            Account account = activeAccount(1000);
            assertThat(account.getVersion()).isNull();
        }

        @Test
        @DisplayName("should reject owner name longer than 255 characters")
        void shouldRejectLongOwnerName() {
            String longName = "a".repeat(256);
            assertThatThrownBy(() -> new Account(1L, new UserId(1L), IBAN, longName, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Owner name can be at most 255 characters");
        }

        @Test
        @DisplayName("should create account with version constructor and reject null userId")
        void shouldRejectNullUserIdInVersionConstructor() {
            assertThatThrownBy(() -> new Account(1L, (UserId) null, IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE, 1L))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should return correct userId")
        void shouldReturnCorrectUserId() {
            Account account = new Account(1L, new UserId(42L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(account.getUserId().value()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("debit")
    class Debit {

        @Test
        @DisplayName("should debit when active and has sufficient balance")
        void shouldDebitSuccessfully() {
            Account account = activeAccount(1000);
            account.debit(Money.of("200.00", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo("800.00");
        }

        @Test
        @DisplayName("should debit to exactly zero balance")
        void shouldDebitToZero() {
            Account account = activeAccount(100);
            account.debit(Money.of("100.00", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        }

        @Test
        @DisplayName("should handle multiple consecutive debits")
        void shouldHandleMultipleDebits() {
            Account account = activeAccount(1000);
            account.debit(Money.of("100.00", Currency.TRY));
            account.debit(Money.of("200.00", Currency.TRY));
            account.debit(Money.of("50.50", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo("649.50");
        }

        @Test
        @DisplayName("should preserve original balance after operation")
        void shouldPreserveBalance() {
            Account account = activeAccount(1000);
            account.debit(Money.of("100.00", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo("900.00");
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when amount exceeds balance")
        void shouldThrowOnOverdraft() {
            Account account = activeAccount(100);
            assertThatThrownBy(() -> account.debit(Money.of("101.00", Currency.TRY)))
                    .isExactlyInstanceOf(InsufficientBalanceException.class)
                    .hasMessage("Insufficient balance. Current: 100.00 TRY, Requested: 101.00 TRY");
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on suspended account")
        void shouldThrowOnSuspendedAccount() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.SUSPENDED);
            assertThatThrownBy(() -> account.debit(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on closed account")
        void shouldThrowOnClosedAccount() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.CLOSED);
            assertThatThrownBy(() -> account.debit(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw CurrencyMismatchException when currency differs")
        void shouldThrowOnCurrencyMismatch() {
            Account account = activeAccount(1000);
            assertThatThrownBy(() -> account.debit(Money.of("50.00", Currency.USD)))
                    .isExactlyInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when amount is null")
        void shouldThrowOnNullAmount() {
            Account account = activeAccount(1000);
            assertThatThrownBy(() -> account.debit(null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw when debit amount is zero")
        void shouldThrowOnZeroDebit() {
            Account account = activeAccount(1000);
            assertThatThrownBy(() -> account.debit(Money.of("0.00", Currency.TRY)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Debit amount must not be zero");
        }

        @Test
        @DisplayName("should debit with fixed clock")
        void shouldDebitWithClock() {
            Account account = activeAccount(1000);
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
            account.debit(Money.of("200.00", Currency.TRY), clock);

            assertThat(account.getBalance().amount()).isEqualByComparingTo("800.00");
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountDebitedEvent);
            AccountDebitedEvent event = (AccountDebitedEvent) account.getDomainEvents().getFirst();
            assertThat(event.occurredAt()).isEqualTo(fixedNow);
        }
    }

    @Nested
    @DisplayName("credit")
    class Credit {

        @Test
        @DisplayName("should credit when active")
        void shouldCreditSuccessfully() {
            Account account = activeAccount(1000);
            account.credit(Money.of("500.00", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("should handle multiple consecutive credits")
        void shouldHandleMultipleCredits() {
            Account account = activeAccount(1000);
            account.credit(Money.of("100.00", Currency.TRY));
            account.credit(Money.of("200.00", Currency.TRY));
            account.credit(Money.of("50.50", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo("1350.50");
        }

        @Test
        @DisplayName("should handle mixed debit and credit sequence")
        void shouldHandleMixedSequence() {
            Account account = activeAccount(500);
            account.credit(Money.of("200.00", Currency.TRY));
            account.debit(Money.of("100.00", Currency.TRY));
            account.credit(Money.of("50.00", Currency.TRY));
            assertThat(account.getBalance().amount()).isEqualByComparingTo("650.00");
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on suspended account")
        void shouldThrowOnSuspendedAccount() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.SUSPENDED);
            assertThatThrownBy(() -> account.credit(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on closed account")
        void shouldThrowOnClosedAccount() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.CLOSED);
            assertThatThrownBy(() -> account.credit(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw CurrencyMismatchException when currency differs")
        void shouldThrowOnCurrencyMismatch() {
            Account account = activeAccount(1000);
            assertThatThrownBy(() -> account.credit(Money.of("50.00", Currency.USD)))
                    .isExactlyInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when amount is null")
        void shouldThrowOnNullAmount() {
            Account account = activeAccount(1000);
            assertThatThrownBy(() -> account.credit(null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw when credit amount is zero")
        void shouldThrowOnZeroCredit() {
            Account account = activeAccount(1000);
            assertThatThrownBy(() -> account.credit(Money.of("0.00", Currency.TRY)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Credit amount must not be zero");
        }

        @Test
        @DisplayName("should credit with fixed clock")
        void shouldCreditWithClock() {
            Account account = activeAccount(1000);
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
            account.credit(Money.of("500.00", Currency.TRY), clock);

            assertThat(account.getBalance().amount()).isEqualByComparingTo("1500.00");
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountCreditedEvent);
            AccountCreditedEvent event = (AccountCreditedEvent) account.getDomainEvents().getFirst();
            assertThat(event.occurredAt()).isEqualTo(fixedNow);
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("isActive should return true for ACTIVE")
        void isActiveTrueForActive() {
            assertThat(activeAccount(1000).isActive()).isTrue();
        }

        @Test
        @DisplayName("isActive should return false for SUSPENDED")
        void isActiveFalseForSuspended() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.SUSPENDED);
            assertThat(account.isActive()).isFalse();
        }

        @Test
        @DisplayName("isActive should return false for CLOSED")
        void isActiveFalseForClosed() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.CLOSED);
            assertThat(account.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("suspend")
    class Suspend {

        @Test
        @DisplayName("should suspend active account")
        void shouldSuspendActive() {
            Account account = activeAccount(1000);
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
            account.suspend(clock);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountSuspendedEvent);
            AccountSuspendedEvent event = (AccountSuspendedEvent) account.getDomainEvents().getFirst();
            assertThat(event.occurredAt()).isEqualTo(fixedNow);
        }

        @Test
        @DisplayName("should be idempotent when already suspended")
        void shouldBeIdempotentWhenSuspended() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.SUSPENDED);
            Clock clock = Clock.fixed(LocalDateTime.of(2026, 6, 24, 12, 0).atZone(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());
            account.suspend(clock);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(account.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("should throw AccountClosedException when closed")
        void shouldThrowWhenClosed() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("0", Currency.TRY),
                    AccountStatus.CLOSED);
            assertThatThrownBy(account::suspend)
                    .isExactlyInstanceOf(AccountClosedException.class);
        }

        @Test
        @DisplayName("should suspend with default clock")
        void shouldSuspendWithDefaultClock() {
            Account account = activeAccount(1000);
            account.suspend();
            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountSuspendedEvent);
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("should close active account with zero balance")
        void shouldCloseWithZeroBalance() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("0.00", Currency.TRY),
                    AccountStatus.ACTIVE);
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
            account.close(clock);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountClosedEvent);
            AccountClosedEvent event = (AccountClosedEvent) account.getDomainEvents().getFirst();
            assertThat(event.occurredAt()).isEqualTo(fixedNow);
        }

        @Test
        @DisplayName("should throw AccountClosedException when already closed")
        void shouldThrowWhenAlreadyClosed() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("0", Currency.TRY),
                    AccountStatus.CLOSED);
            assertThatThrownBy(account::close)
                    .isExactlyInstanceOf(AccountClosedException.class);
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when balance is not zero")
        void shouldThrowWhenBalanceNotZero() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("100.00", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThatThrownBy(account::close)
                    .isExactlyInstanceOf(InsufficientBalanceException.class);
        }

        @Test
        @DisplayName("should close SUSPENDED account with zero balance and record timestamp")
        void shouldCloseSuspendedAccount() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 24, 12, 0);
            Clock clock = Clock.fixed(fixedNow.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("0.00", Currency.TRY),
                    AccountStatus.SUSPENDED);
            account.close(clock);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountClosedEvent);
            AccountClosedEvent event = (AccountClosedEvent) account.getDomainEvents().getFirst();
            assertThat(event.occurredAt()).isEqualTo(fixedNow);
        }

        @Test
        @DisplayName("should close active account with zero balance using default clock")
        void shouldCloseWithDefaultClock() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("0.00", Currency.TRY),
                    AccountStatus.ACTIVE);
            account.close();
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getDomainEvents())
                    .hasSize(1)
                    .allMatch(e -> e instanceof AccountClosedEvent);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true when same ID")
        void equalsWhenSameId() {
            Account a1 = new Account(1L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            Account a2 = new Account(1L, new UserId(2L), new Iban("TR290006200000000000000222"), "Mehmet",
                    Money.of("500", Currency.TRY), AccountStatus.SUSPENDED);
            assertThat(a1).isEqualTo(a2);
        }

        @Test
        @DisplayName("equals should return false when different ID")
        void notEqualsWhenDifferentId() {
            Account a1 = new Account(1L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            Account a2 = new Account(2L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(a1).isNotEqualTo(a2);
        }

        @Test
        @DisplayName("equals should return false when both IDs are null")
        void notEqualsWhenBothNullIds() {
            Account a1 = new Account(null, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            Account a2 = new Account(null, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(a1).isNotEqualTo(a2);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            Account a1 = new Account(1L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(a1).isNotEqualTo("some-string");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            Account a1 = new Account(1L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            Account a2 = new Account(1L, new UserId(2L), new Iban("TR290006200000000000000222"), "Mehmet",
                    Money.of("500", Currency.TRY), AccountStatus.SUSPENDED);
            assertThat(a1).hasSameHashCodeAs(a2);
        }

        @Test
        @DisplayName("hashCode should be based on id")
        void hashCodeShouldBeBasedOnId() {
            Account a1 = new Account(1L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            Account a2 = new Account(1L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            Account a3 = new Account(2L, new UserId(1L), IBAN, "Ahmet", Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
            assertThat(a1.hashCode()).isNotEqualTo(a3.hashCode());
        }

        @Test
        @DisplayName("equals should return true for same reference")
        void equalsWhenSameReference() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(account).isEqualTo(account);
        }

        @Test
        @DisplayName("hashCode should return 0 when id is null")
        void hashCodeWhenNullId() {
            Account account = new Account(null, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(account.hashCode()).isZero();
        }

        @Test
        @DisplayName("toString should contain account fields")
        void toStringShouldContainFields() {
            Account account = new Account(1L, new UserId(1L), IBAN, OWNER, Money.of("1000", Currency.TRY),
                    AccountStatus.ACTIVE);
            assertThat(account.toString()).contains("id=1", "iban=", "status=ACTIVE", "balance=");
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            Account account = Account.builder()
                    .id(1L)
                    .userId(new UserId(100L))
                    .iban(IBAN)
                    .ownerName("Ahmet")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .version(3L)
                    .build();

            assertThat(account.getId()).isEqualTo(1L);
            assertThat(account.getUserId().value()).isEqualTo(100L);
            assertThat(account.getOwnerName()).isEqualTo("Ahmet");
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should build with null version")
        void shouldBuildWithNullVersion() {
            Account account = Account.builder()
                    .id(1L)
                    .userId(new UserId(100L))
                    .iban(IBAN)
                    .ownerName("Ahmet")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .build();

            assertThat(account.getVersion()).isNull();
        }
    }
}
