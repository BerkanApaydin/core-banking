package com.bank.app.transfer.application.port.out;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Money;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Anti-corruption port shielding the transfer domain from the Account context.
 * Reads are translated into transfer-owned {@link AccountInfo}; balance
 * mutations return the transfer-owned opaque {@link MutationResult} — never the
 * Account context's published-language type directly. Account domain events
 * never cross this boundary (the Account context publishes them itself).
 */
public interface AccountAclPort {
    AccountInfo getAccountInfo(AccountId accountId);

    AccountInfo getAccountInfoForTransfer(String ibanValue);

    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);

    MutationResult debitAndCredit(AccountId senderId, AccountId receiverId, Money amount);

    MutationResult reverseBalancesForCancellation(AccountId senderId, AccountId receiverId, Money amount);

    record AccountInfo(AccountId id, Long userId, String currency, String status) {
        public AccountInfo {
            Objects.requireNonNull(id);
            Objects.requireNonNull(userId);
            Objects.requireNonNull(currency);
            Objects.requireNonNull(status);
        }
    }

    /**
     * Transfer-owned view of a balance mutation. Mapped from the Account
     * context's published language ({@code AccountAdjustmentResult}) inside the
     * ACL adapter, so application code never depends on another BC's types.
     */
    record MutationResult(
            AccountId senderAccountId,
            AccountId receiverAccountId,
            Money senderNewBalance,
            Money receiverNewBalance) {
        public MutationResult {
            Objects.requireNonNull(senderAccountId, "senderAccountId must not be null");
            Objects.requireNonNull(receiverAccountId, "receiverAccountId must not be null");
            Objects.requireNonNull(senderNewBalance, "senderNewBalance must not be null");
            Objects.requireNonNull(receiverNewBalance, "receiverNewBalance must not be null");
        }
    }
}
