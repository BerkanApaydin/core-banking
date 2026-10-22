package com.bank.app.accountapi;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountApi published language contract")
class AccountApiContractTest {

    @Test
    void snapshotShouldRejectNullFields() {
        assertThrows(NullPointerException.class, () -> new AccountSnapshot(null, 1L, "TRY", "ACTIVE"));
        assertThrows(NullPointerException.class, () -> new AccountSnapshot(1L, null, "TRY", "ACTIVE"));
        assertThrows(NullPointerException.class, () -> new AccountSnapshot(1L, 1L, null, "ACTIVE"));
        assertThrows(NullPointerException.class, () -> new AccountSnapshot(1L, 1L, "TRY", null));
    }

    @Test
    void adjustmentResultShouldRejectNullFields() {
        Money balance = Money.of("100.00", Currency.TRY);
        assertThrows(NullPointerException.class, () -> new AccountAdjustmentResult(null, 2L, balance, balance));
        assertThrows(NullPointerException.class, () -> new AccountAdjustmentResult(1L, null, balance, balance));
        assertThrows(NullPointerException.class, () -> new AccountAdjustmentResult(1L, 2L, null, balance));
        assertThrows(NullPointerException.class, () -> new AccountAdjustmentResult(1L, 2L, balance, null));
    }

    @Test
    void notFoundShouldExposeNotFoundKind() {
        assertEquals(BusinessFailureKind.NOT_FOUND,
                new AccountNotFoundException(7L).getFailureKind());
        assertEquals(BusinessFailureKind.NOT_FOUND,
                new AccountNotFoundException("TR440006200000000000000123").getFailureKind());
    }

    @Test
    void ibanKeyShouldNormalize() {
        // Kills the EmptyObjectReturn mutant ("" vs normalized key).
        assertEquals("TR440006200000000000000123",
                AccountSnapshotCache.ibanKey("tr44 0006 2000 0000 0000 0001 23"));
    }

    @Test
    void adjustmentResultShouldCarryPostTransactionBalances() {
        AccountAdjustmentResult result = new AccountAdjustmentResult(
                1L, 2L,
                Money.of("800.00", Currency.TRY),
                Money.of("700.00", Currency.TRY));

        assertEquals(1L, result.senderAccountId());
        assertEquals(2L, result.receiverAccountId());
        assertEquals(Money.of("800.00", Currency.TRY), result.senderNewBalance());
        assertEquals(Money.of("700.00", Currency.TRY), result.receiverNewBalance());
    }

    /**
     * The published language does NOT reject a self-transfer: the guard lives
     * in the account domain (DistinctAccountsRequiredException) and in the
     * transfer domain (SameAccountTransferException). A consumer must not
     * rely on this record to catch it — it only carries post-transaction
     * balances for observability.
     */
    @Test
    void adjustmentResultShouldAcceptASelfTransfer() {
        Money balance = Money.of("100.00", Currency.TRY);

        AccountAdjustmentResult result = new AccountAdjustmentResult(1L, 1L, balance, balance);

        assertEquals(1L, result.senderAccountId());
        assertEquals(1L, result.receiverAccountId());
    }

    /**
     * The published language does NOT reject a currency mismatch between the
     * two balances either: the account domain raises
     * {@code CurrencyMismatchException} before a result is ever produced.
     */
    @Test
    void adjustmentResultShouldAcceptMismatchedCurrencies() {
        AccountAdjustmentResult result = new AccountAdjustmentResult(
                1L, 2L,
                Money.of("100.00", Currency.TRY),
                Money.of("100.00", Currency.USD));

        assertEquals(Currency.TRY, result.senderNewBalance().currency());
        assertEquals(Currency.USD, result.receiverNewBalance().currency());
    }

    @Test
    void notFoundShouldMaskTheIbanInTheMessageAndInTheMessageArguments() {
        // O-12: the raw IBAN must never reach a log line or an API response.
        String iban = "TR440006200000000000000123";

        AccountNotFoundException ex = new AccountNotFoundException(iban);

        assertFalse(ex.getMessage().contains(iban),
                "the raw IBAN must not appear in the exception message");
        assertTrue(ex.getMessage().contains("TR440006*******0123"));
        assertArrayEquals(new Object[]{"TR440006*******0123"}, ex.getArgs(),
                "the i18n arguments are interpolated into the client response");
    }

    @Test
    void notFoundShouldNotEchoTheFullIbanOfAShortOrBlankValue() {
        String garbage = "TR00";

        AccountNotFoundException ex = new AccountNotFoundException(garbage);

        assertFalse(ex.getMessage().contains(garbage));
        assertTrue(ex.getMessage().contains("***"));
    }
}
