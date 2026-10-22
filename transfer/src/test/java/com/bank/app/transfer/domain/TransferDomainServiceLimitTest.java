package com.bank.app.transfer.domain;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.TransferCurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Money rules enforced at the transfer domain boundary.
 *
 * <p>{@code TransferDomainService} is the inner guarantee of the transfer
 * module: even a programmatic caller that bypasses the web layer must not be
 * able to create an over-ceiling, zero or cross-currency transfer.
 */
@SuppressWarnings("null")
@DisplayName("TransferDomainService money rules")
class TransferDomainServiceLimitTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);
    private static final String SENDER_IBAN = "TR770006200000000000000111";
    private static final String RECEIVER_IBAN = "TR870006200000000000000222";

    private final TransferDomainService domainService = new TransferDomainService();

    private static TransferParticipants participants(Currency senderCurrency, Currency receiverCurrency) {
        return new TransferParticipants(
                new AccountId(1L), SENDER_IBAN, senderCurrency,
                new AccountId(2L), RECEIVER_IBAN, receiverCurrency);
    }

    @Test
    @DisplayName("should accept a transfer exactly at the single-transfer ceiling")
    void shouldAcceptTransferExactlyAtTheCeiling() {
        Transfer transfer = domainService.validateAndCreateTransfer(
                participants(Currency.TRY, Currency.TRY),
                Money.of("1000000000.00", Currency.TRY), FIXED_CLOCK);

        // The ceiling itself is a legal amount: rejecting it would block the
        // largest transfer the contract allows.
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getAmount().amount()).isEqualByComparingTo("1000000000.00");
    }

    @Test
    @DisplayName("should reject a transfer one cent above the ceiling")
    void shouldRejectTransferOneCentAboveTheCeiling() {
        assertThatThrownBy(() -> domainService.validateAndCreateTransfer(
                participants(Currency.TRY, Currency.TRY),
                Money.of("1000000000.01", Currency.TRY), FIXED_CLOCK))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transfer amount exceeds maximum of 1000000000.00");
    }

    @Test
    @DisplayName("should never carry a negative amount")
    void shouldNeverCarryNegativeAmount() {
        // A negative amount cannot even be represented as Money, so it can
        // never reach the transfer aggregate (a debit with a negative sign
        // would credit the sender).
        assertThatThrownBy(() -> Money.of("-1.00", Currency.TRY))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must not be negative");
    }

    @Test
    @DisplayName("should never carry an amount with sub-cent precision")
    void shouldNeverCarrySubCentAmount() {
        // Sub-cent amounts must be rejected, not silently rounded: rounding
        // here would move money the customer never asked for.
        assertThatThrownBy(() -> Money.of("100.123", Currency.TRY))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount can have at most 2 decimal places");
    }

    @Test
    @DisplayName("should reject a sender currency mismatch with the transfer specific error code")
    void shouldRejectSenderCurrencyMismatchWithTransferErrorCode() {
        assertThatThrownBy(() -> domainService.validateAndCreateTransfer(
                participants(Currency.USD, Currency.TRY),
                Money.of("100.00", Currency.TRY), FIXED_CLOCK))
                .isExactlyInstanceOf(TransferCurrencyMismatchException.class)
                .isInstanceOfSatisfying(TransferCurrencyMismatchException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("TRANSFER_CURRENCY_MISMATCH"));
    }

    @Test
    @DisplayName("should reject a receiver currency mismatch with the transfer specific error code")
    void shouldRejectReceiverCurrencyMismatchWithTransferErrorCode() {
        assertThatThrownBy(() -> domainService.validateAndCreateTransfer(
                participants(Currency.TRY, Currency.USD),
                Money.of("100.00", Currency.TRY), FIXED_CLOCK))
                .isExactlyInstanceOf(TransferCurrencyMismatchException.class)
                .isInstanceOfSatisfying(TransferCurrencyMismatchException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("TRANSFER_CURRENCY_MISMATCH"));
    }
}
