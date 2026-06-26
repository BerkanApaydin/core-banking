package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("Money value object")
class MoneyTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @ParameterizedTest(name = "should create Money from string {0} with currency {1}")
        @CsvSource({
            "100.50, TRY",
            "0.00, USD",
            "999999999.99, EUR",
            "0.01, TRY"
        })
        void shouldCreateFromValidString(String amount, Currency currency) {
            Money money = Money.of(amount, currency);
            assertThat(money.amount()).isEqualByComparingTo(amount);
            assertThat(money.currency()).isEqualTo(currency);
        }

        @ParameterizedTest(name = "should create Money from BigDecimal {0}")
        @CsvSource({
            "100.00, TRY",
            "0.00, USD",
            "999999999.99, EUR"
        })
        void shouldCreateFromBigDecimal(String amount, Currency currency) {
            Money money = Money.of(new BigDecimal(amount), currency);
            assertThat(money.amount()).isEqualByComparingTo(amount);
            assertThat(money.currency()).isEqualTo(currency);
        }

        @Test
        @DisplayName("should round to 2 decimal places on factory method")
        void shouldRoundOnFactory() {
            Money money = Money.of("100.456", Currency.TRY);
            assertThat(money.amount()).isEqualByComparingTo("100.46");
        }

        @Test
        @DisplayName("should have scale of 2")
        void shouldHaveScale2() {
            Money money = Money.of("100.00", Currency.TRY);
            org.junit.jupiter.api.Assertions.assertEquals(2, money.amount().scale());
        }

        @Test
        @DisplayName("should support all three currencies")
        void shouldSupportAllCurrencies() {
            assertThat(Money.of("100.00", Currency.TRY)).isNotNull();
            assertThat(Money.of("100.00", Currency.USD)).isNotNull();
            assertThat(Money.of("100.00", Currency.EUR)).isNotNull();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @ParameterizedTest(name = "should reject null {0}")
        @ValueSource(strings = {"amount", "currency"})
        @DisplayName("should reject null constructor arguments")
        void shouldRejectNullConstructorArgs(String field) {
            assertThatThrownBy(() -> {
                if ("amount".equals(field)) {
                    new Money(null, Currency.TRY);
                } else {
                    new Money(BigDecimal.ONE, null);
                }
            }).isExactlyInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @MethodSource("nullOperandProvider")
        @DisplayName("should reject null operands")
        void shouldRejectNullOperands(String label, OperandInvocation invocation) {
            Money money = Money.of("100.00", Currency.TRY);
            assertThatThrownBy(() -> invocation.apply(money))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        static Stream<Arguments> nullOperandProvider() {
            return Stream.of(
                    Arguments.of("add", (OperandInvocation) m -> m.add(null)),
                    Arguments.of("subtract", (OperandInvocation) m -> m.subtract(null)),
                    Arguments.of("isGreaterThan", (OperandInvocation) m -> m.isGreaterThan(null)),
                    Arguments.of("isGreaterThanOrEqual", (OperandInvocation) m -> m.isGreaterThanOrEqual(null))
            );
        }

        @ParameterizedTest(name = "should reject negative amount: {0}")
        @ValueSource(strings = {"-10.00", "-0.01"})
        @DisplayName("should reject negative amounts")
        void shouldRejectNegativeAmount(String value) {
            assertThatThrownBy(() -> new Money(new BigDecimal(value), Currency.TRY))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Para tutarı negatif olamaz");
        }

        @Test
        @DisplayName("should reject scale larger than 2")
        void shouldRejectExcessiveScale() {
            assertThatThrownBy(() -> new Money(new BigDecimal("100.555"), Currency.TRY))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject invalid string format")
        void shouldRejectInvalidString() {
            assertThatThrownBy(() -> Money.of("abc", Currency.TRY))
                    .isExactlyInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("should reject string with trailing characters")
        void shouldRejectStringWithTrailingChars() {
            assertThatThrownBy(() -> Money.of("100.00XYZ", Currency.TRY))
                    .isExactlyInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("should reject null string factory argument")
        void shouldRejectNullStringFactory() {
            assertThatThrownBy(() -> Money.of((String) null, Currency.TRY))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null BigDecimal factory argument")
        void shouldRejectNullBigDecimalFactory() {
            assertThatThrownBy(() -> Money.of((BigDecimal) null, Currency.TRY))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("should add two amounts with same currency")
        void shouldAdd() {
            Money result = Money.of("100.00", Currency.TRY).add(Money.of("50.25", Currency.TRY));
            assertThat(result.amount()).isEqualByComparingTo("150.25");
        }

        @Test
        @DisplayName("should subtract two amounts with same currency")
        void shouldSubtract() {
            Money result = Money.of("100.00", Currency.TRY).subtract(Money.of("30.00", Currency.TRY));
            assertThat(result.amount()).isEqualByComparingTo("70.00");
        }

        @Test
        @DisplayName("should subtract to zero")
        void shouldSubtractToZero() {
            Money result = Money.of("100.00", Currency.TRY).subtract(Money.of("100.00", Currency.TRY));
            assertThat(result.amount()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        }

        @Test
        @DisplayName("should throw when subtraction results in negative")
        void shouldThrowOnNegativeResult() {
            assertThatThrownBy(() ->
                    Money.of("50.00", Currency.TRY).subtract(Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Para tutarı negatif olamaz");
        }

        @Test
        @DisplayName("should not mutate original on add")
        void shouldNotMutateOnAdd() {
            Money original = Money.of("100.00", Currency.TRY);
            original.add(Money.of("50.00", Currency.TRY));
            original.add(Money.of("25.00", Currency.TRY));
            assertThat(original.amount()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should chain operations correctly")
        void shouldChainOperations() {
            Money result = Money.of("100.00", Currency.TRY)
                    .add(Money.of("50.00", Currency.TRY))
                    .subtract(Money.of("30.00", Currency.TRY));
            assertThat(result.amount()).isEqualByComparingTo("120.00");
        }
    }

    @Nested
    @DisplayName("isZero")
    class IsZero {

        @Test
        @DisplayName("should return true when amount is zero")
        void shouldReturnTrueForZero() {
            assertThat(Money.of("0.00", Currency.TRY).isZero()).isTrue();
        }

        @Test
        @DisplayName("should return false when amount is non-zero")
        void shouldReturnFalseForNonZero() {
            assertThat(Money.of("0.01", Currency.TRY).isZero()).isFalse();
        }

        @Test
        @DisplayName("should return false for positive amount")
        void shouldReturnFalseForPositive() {
            assertThat(Money.of("100.00", Currency.TRY).isZero()).isFalse();
        }
    }

    @Nested
    @DisplayName("comparison")
    class Comparison {

        @Test
        @DisplayName("isGreaterThan should return true when greater")
        void isGreaterThanShouldReturnTrue() {
            assertThat(Money.of("200.00", Currency.TRY).isGreaterThan(Money.of("100.00", Currency.TRY)))
                    .isTrue();
        }

        @Test
        @DisplayName("isGreaterThan should return false when equal")
        void isGreaterThanShouldReturnFalseWhenEqual() {
            assertThat(Money.of("100.00", Currency.TRY).isGreaterThan(Money.of("100.00", Currency.TRY)))
                    .isFalse();
        }

        @Test
        @DisplayName("isGreaterThanOrEqual should return true when equal")
        void isGreaterThanOrEqualShouldReturnTrueWhenEqual() {
            assertThat(Money.of("100.00", Currency.TRY).isGreaterThanOrEqual(Money.of("100.00", Currency.TRY)))
                    .isTrue();
        }

        @Test
        @DisplayName("isGreaterThanOrEqual should return true when greater")
        void isGreaterThanOrEqualShouldReturnTrueWhenGreater() {
            assertThat(Money.of("200.00", Currency.TRY).isGreaterThanOrEqual(Money.of("100.00", Currency.TRY)))
                    .isTrue();
        }

        @Test
        @DisplayName("isGreaterThanOrEqual should return false when less")
        void isGreaterThanOrEqualShouldReturnFalseWhenLess() {
            assertThat(Money.of("50.00", Currency.TRY).isGreaterThanOrEqual(Money.of("100.00", Currency.TRY)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("currency mismatch")
    class CurrencyMismatch {

        @ParameterizedTest(name = "should throw on {0} with different currencies")
        @MethodSource("mismatchOperations")
        @DisplayName("should throw CurrencyMismatchException when currencies differ")
        void shouldThrowOnMismatch(String label, MismatchOperation operation) {
            Money m1 = Money.of("100.00", Currency.TRY);
            Money m2 = Money.of("50.00", Currency.USD);
            assertThatThrownBy(() -> operation.apply(m1, m2))
                    .isExactlyInstanceOf(CurrencyMismatchException.class);
        }

        static Stream<Arguments> mismatchOperations() {
            return Stream.of(
                    Arguments.of("add", (MismatchOperation) Money::add),
                    Arguments.of("subtract", (MismatchOperation) Money::subtract),
                    Arguments.of("isGreaterThan", (MismatchOperation) Money::isGreaterThan),
                    Arguments.of("isGreaterThanOrEqual", (MismatchOperation) Money::isGreaterThanOrEqual)
            );
        }
    }

    @FunctionalInterface
    interface OperandInvocation {
        void apply(Money money);
    }

    @FunctionalInterface
    interface MismatchOperation {
        void apply(Money m1, Money m2);
    }
}
