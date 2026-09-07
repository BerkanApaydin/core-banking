package com.bank.app.accountapi;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
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
}
