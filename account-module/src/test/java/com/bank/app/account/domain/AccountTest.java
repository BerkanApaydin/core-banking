package com.bank.app.account.domain;

import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Account domain entity")
class AccountTest {

    private static final Iban IBAN = new Iban("TR290006200000000000000111");
    private static final String OWNER = "Ahmet Yılmaz";

    private Account activeAccount(long balance) {
        return new Account(1L, 1L, IBAN, OWNER, Money.of(String.valueOf(balance), Currency.TRY), AccountStatus.ACTIVE);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @ParameterizedTest(name = "should reject null {0}")
        @NullSource
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullArgs(String nullValue) {
            assertThatThrownBy(() -> new Account(1L, 1L, null, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, 1L, IBAN, null, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, 1L, IBAN, OWNER, null, AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, null, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest(name = "should reject blank owner name: \"{0}\"")
        @ValueSource(strings = {"", "   "})
        @DisplayName("should reject blank owner names")
        void shouldRejectBlankOwnerName(String name) {
            assertThatThrownBy(() -> new Account(1L, 1L, IBAN, name, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should create account with null ID")
        void shouldCreateWithNullId() {
            Account account = new Account(null, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            assertThat(account.getId()).isNull();
        }

        @Test
        @DisplayName("should create account with version")
        void shouldCreateWithVersion() {
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE, 5L);
            assertThat(account.getVersion()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should create account with null version by default")
        void shouldCreateWithNullVersion() {
            Account account = activeAccount(1000);
            assertThat(account.getVersion()).isNull();
        }

        @Test
        @DisplayName("should create account with version constructor and reject null userId")
        void shouldRejectNullUserIdInVersionConstructor() {
            assertThatThrownBy(() -> new Account(1L, null, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE, 1L))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should return correct userId")
        void shouldReturnCorrectUserId() {
            Account account = new Account(1L, 42L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            assertThat(account.getUserId()).isEqualTo(42L);
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
                    .hasMessage("Bakiye yetersiz. Mevcut: 100.00 TRY, İstenen: 101.00 TRY");
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on suspended account")
        void shouldThrowOnSuspendedAccount() {
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.SUSPENDED);
            assertThatThrownBy(() -> account.debit(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on closed account")
        void shouldThrowOnClosedAccount() {
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.CLOSED);
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
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.SUSPENDED);
            assertThatThrownBy(() -> account.credit(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw AccountNotActiveException on closed account")
        void shouldThrowOnClosedAccount() {
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.CLOSED);
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
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.SUSPENDED);
            assertThat(account.isActive()).isFalse();
        }

        @Test
        @DisplayName("isActive should return false for CLOSED")
        void isActiveFalseForClosed() {
            Account account = new Account(1L, 1L, IBAN, OWNER, Money.of("1000", Currency.TRY), AccountStatus.CLOSED);
            assertThat(account.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equals should return true when same ID")
        void equalsWhenSameId() {
            Account a1 = new Account(1L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            Account a2 = new Account(1L, 2L, new Iban("TR290006200000000000000222"), "Mehmet", Money.of("500", Currency.TRY), AccountStatus.SUSPENDED);
            assertThat(a1).isEqualTo(a2);
        }

        @Test
        @DisplayName("equals should return false when different ID")
        void notEqualsWhenDifferentId() {
            Account a1 = new Account(1L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            Account a2 = new Account(2L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            assertThat(a1).isNotEqualTo(a2);
        }

        @Test
        @DisplayName("equals should return false when both IDs are null")
        void notEqualsWhenBothNullIds() {
            Account a1 = new Account(null, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            Account a2 = new Account(null, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            assertThat(a1).isNotEqualTo(a2);
        }

        @Test
        @DisplayName("equals should return false for different type")
        void notEqualsForDifferentType() {
            Account a1 = new Account(1L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            assertThat(a1).isNotEqualTo("some-string");
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            Account a1 = new Account(1L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            Account a2 = new Account(1L, 2L, new Iban("TR290006200000000000000222"), "Mehmet", Money.of("500", Currency.TRY), AccountStatus.SUSPENDED);
            assertThat(a1).hasSameHashCodeAs(a2);
        }

        @Test
        @DisplayName("hashCode should differ for different IDs")
        void hashCodeDiffersForDifferentIds() {
            Account a1 = new Account(1L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            Account a2 = new Account(2L, 1L, IBAN, "Ahmet", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);
            assertThat(a1.hashCode()).isNotEqualTo(a2.hashCode());
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
                    .userId(100L)
                    .iban(IBAN)
                    .ownerName("Ahmet")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .version(3L)
                    .build();

            assertThat(account.getId()).isEqualTo(1L);
            assertThat(account.getUserId()).isEqualTo(100L);
            assertThat(account.getOwnerName()).isEqualTo("Ahmet");
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should build with null version")
        void shouldBuildWithNullVersion() {
            Account account = Account.builder()
                    .id(1L)
                    .userId(100L)
                    .iban(IBAN)
                    .ownerName("Ahmet")
                    .balance(Money.of("1000.00", Currency.TRY))
                    .status(AccountStatus.ACTIVE)
                    .build();

            assertThat(account.getVersion()).isNull();
        }
    }
}
