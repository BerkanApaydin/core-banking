package com.bank.app.accountapi;

import com.bank.app.common.domain.Money;

import java.util.Collection;
import java.util.Map;

/**
 * Open Host Service of the Account bounded context.
 *
 * <p>Implemented by the {@code account} module, consumed by downstream contexts
 * (e.g. {@code transfer} via its anti-corruption layer). Consumers depend only
 * on this interface plus the immutable DTOs in this package — never on
 * {@code com.bank.app.account..} application or domain types.
 */
public interface AccountApi {

    AccountSnapshot getSnapshotById(Long accountId);

    AccountSnapshot getSnapshotByIban(String ibanValue);

    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);

    AccountAdjustmentResult adjustBalances(Long senderId, Long receiverId, Money amount);

    AccountAdjustmentResult reverseForCancellation(Long senderId, Long receiverId, Money amount);
}
