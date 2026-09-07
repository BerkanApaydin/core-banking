package com.bank.app.account.application.port.in;

import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.common.domain.Money;

/**
 * Balance-adjustment capability of the Account context.
 *
 * <p>Also exposed to downstream contexts through the {@code account-api}
 * published language ({@code AccountApiAdapter} delegates to this port).
 * Implementations publish the resulting domain events themselves, so callers
 * only see the opaque {@link AccountAdjustmentResult} — account domain events
 * never cross the context boundary.
 */
public interface AdjustAccountBalancesUseCase {
    AccountAdjustmentResult debitAndCredit(Long senderId, Long receiverId, Money amount);

    AccountAdjustmentResult reverseForCancellation(Long senderId, Long receiverId, Money amount);
}
