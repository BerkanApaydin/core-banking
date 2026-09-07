package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountInfoCachePort;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Anti-corruption adapter: implements transfer's {@link AccountAclPort} by
 * delegating to the Account context's published language ({@link AccountApi}).
 * This module depends only on {@code account-api}, never on the
 * {@code account} module itself.
 *
 * <p>Caching goes through {@link AccountInfoCachePort} (infrastructure owns the
 * backend) instead of Spring-Cache annotations, so this adapter stays
 * framework-free. Reads populate the cache; balance mutations evict only the two
 * involved accounts (granular) so unrelated entries never stampede.
 */
public class AccountAclAdapter implements AccountAclPort {

    private final AccountApi accountApi;
    private final AccountInfoCachePort cache;

    public AccountAclAdapter(AccountApi accountApi, AccountInfoCachePort cache) {
        this.accountApi = Objects.requireNonNull(accountApi, "AccountApi must not be null");
        this.cache = Objects.requireNonNull(cache, "AccountInfoCachePort must not be null");
    }

    @Override
    public AccountAclPort.AccountInfo getAccountInfo(Long accountId) {
        return cache.getById(accountId)
                .orElseGet(() -> {
                    AccountAclPort.AccountInfo info = toAccountInfo(accountApi.getSnapshotById(accountId));
                    cache.putById(accountId, info);
                    return info;
                });
    }

    @Override
    public AccountAclPort.AccountInfo getAccountInfoForTransfer(String ibanValue) {
        return cache.getByIban(ibanValue)
                .orElseGet(() -> {
                    AccountAclPort.AccountInfo info = toAccountInfo(accountApi.getSnapshotByIban(ibanValue));
                    cache.putByIban(ibanValue, info);
                    return info;
                });
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return cache.getIbans(accountIds)
                .orElseGet(() -> {
                    Map<Long, String> ibans = accountApi.getIbansForAccounts(accountIds);
                    cache.putIbans(accountIds, ibans);
                    return ibans;
                });
    }

    @Override
    public AccountAclPort.MutationResult debitAndCredit(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        AccountAdjustmentResult result = accountApi.adjustBalances(senderId, receiverId, amount);
        evictMutatedAccounts(senderId, receiverId);
        return toMutationResult(result);
    }

    @Override
    public AccountAclPort.MutationResult reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        AccountAdjustmentResult result = accountApi.reverseForCancellation(senderId, receiverId, amount);
        evictMutatedAccounts(senderId, receiverId);
        return toMutationResult(result);
    }

    private void evictMutatedAccounts(Long senderId, Long receiverId) {
        // Granular invalidation: only the two mutated accounts + batch lookups that may
        // reference them. Cached AccountInfo carries no balance (id/userId/currency/status),
        // and IBAN mappings are immutable, so unrelated entries stay valid.
        cache.evictById(senderId);
        cache.evictById(receiverId);
        cache.evictIbansBatch();
    }

    private static AccountAclPort.AccountInfo toAccountInfo(AccountSnapshot snapshot) {
        return new AccountAclPort.AccountInfo(
                snapshot.id(), snapshot.userId(), snapshot.currency(), snapshot.status());
    }

    private static AccountAclPort.MutationResult toMutationResult(AccountAdjustmentResult result) {
        return new AccountAclPort.MutationResult(
                result.senderAccountId(),
                result.receiverAccountId(),
                result.senderNewBalance(),
                result.receiverNewBalance());
    }
}
