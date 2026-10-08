package com.bank.app.account.domain;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LedgerEntry")
class LedgerEntryTest {

    private static final Money AMOUNT = Money.of("200.00", Currency.TRY);
    private static final Money BALANCE = Money.of("800.00", Currency.TRY);

    @Test
    @DisplayName("debit and credit factories assign direction and timestamp")
    void shouldBuildLegs() {
        TransactionRef ref = LedgerEntry.newTransactionRef();
        Clock clock = Clock.systemUTC();

        LedgerEntry debit = LedgerEntry.debit(1L, AMOUNT, BALANCE, ref, clock);
        LedgerEntry credit = LedgerEntry.credit(2L, AMOUNT, BALANCE, ref, clock);

        assertThat(debit.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(credit.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(debit.getTransactionRef()).isEqualTo(ref);
        assertThat(credit.getTransactionRef()).isEqualTo(ref);
        assertThat(debit.getId()).isNull();
        assertThat(debit.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("transaction refs are unique per operation")
    void shouldGenerateUniqueRefs() {
        assertThat(LedgerEntry.newTransactionRef()).isNotEqualTo(LedgerEntry.newTransactionRef());
        assertThatCode(() -> UUID.fromString(LedgerEntry.newTransactionRef().value())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("transaction ref value object rejects blank and oversized refs")
    void shouldValidateRef() {
        assertThatThrownBy(() -> new TransactionRef("  "))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransactionRef("r".repeat(37)))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThat(new TransactionRef("ref-1").value()).isEqualTo("ref-1");
    }

    @Test
    @DisplayName("should reject zero amount")
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> new LedgerEntry(null, new TransactionRef("ref"), 1L, LedgerDirection.DEBIT,
                Money.of("0.00", Currency.TRY), BALANCE, LocalDateTime.now()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject blank transaction ref")
    void shouldRejectBlankRef() {
        assertThatThrownBy(() -> new LedgerEntry(null, new TransactionRef("  "), 1L, LedgerDirection.DEBIT,
                AMOUNT, BALANCE, LocalDateTime.now()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject currency mismatch between amount and balance")
    void shouldRejectCurrencyMismatch() {
        assertThatThrownBy(() -> new LedgerEntry(null, new TransactionRef("ref"), 1L, LedgerDirection.DEBIT,
                AMOUNT, Money.of("800.00", Currency.USD), LocalDateTime.now()))
                .hasMessageContaining("cannot be journaled together");
    }

    @Test
    @DisplayName("debitFromBefore derives balanceAfter by subtraction")
    void shouldDeriveDebitBalanceAfter() {
        Money before = Money.of("1000.00", Currency.TRY);
        LedgerEntry leg = LedgerEntry.debitFromBefore(1L, AMOUNT, before, new TransactionRef("ref-1"), Clock.systemUTC());

        assertThat(leg.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(leg.getBalanceAfter()).isEqualTo(Money.of("800.00", Currency.TRY));
        assertThat(leg.getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("creditFromBefore derives balanceAfter by addition")
    void shouldDeriveCreditBalanceAfter() {
        Money before = Money.of("500.00", Currency.TRY);
        LedgerEntry leg = LedgerEntry.creditFromBefore(2L, AMOUNT, before, new TransactionRef("ref-2"), Clock.systemUTC());

        assertThat(leg.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(leg.getBalanceAfter()).isEqualTo(Money.of("700.00", Currency.TRY));
    }

    @Test
    @DisplayName("toString renders entry fields")
    void shouldRenderToString() {
        LedgerEntry leg = LedgerEntry.debit(1L, AMOUNT, BALANCE, new TransactionRef("ref-9"), Clock.systemUTC());

        assertThat(leg.toString())
                .contains("account=1")
                .contains("direction=DEBIT")
                .contains("ref-9");
    }
}
