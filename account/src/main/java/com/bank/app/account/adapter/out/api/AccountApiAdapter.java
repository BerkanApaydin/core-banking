package com.bank.app.account.adapter.out.api;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.accountapi.AccountNotFoundException;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Outbound API adapter (D14/S12): implements the {@code account-api} published
 * language (Open Host Service) by delegating to the account application ports.
 * It lives in {@code adapter.out} because the Account context <em>serves</em>
 * this contract outward — downstream contexts program against {@link AccountApi},
 * never against account use-cases or domain types.
 *
 * <p>Cache-invalidation ownership: every balance/status mutation performed here
 * evicts the involved snapshots through {@link AccountSnapshotCache} before
 * returning, so downstream read-through caches can never serve pre-transaction
 * values (including a stale ACTIVE status after suspend/close). Callers must
 * never evict on this adapter's behalf — and future mutating methods must
 * evict the same way (enforced by CacheInvalidationArchitectureTest).
 */
@Component
public class AccountApiAdapter implements AccountApi {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AdjustAccountBalancesUseCase adjustAccountBalancesUseCase;
    private final AccountSnapshotCache snapshotCache;

    public AccountApiAdapter(AccountQueryUseCase accountQueryUseCase,
            AdjustAccountBalancesUseCase adjustAccountBalancesUseCase,
            AccountSnapshotCache snapshotCache) {
        this.accountQueryUseCase = accountQueryUseCase;
        this.adjustAccountBalancesUseCase = adjustAccountBalancesUseCase;
        this.snapshotCache = Objects.requireNonNull(snapshotCache, "AccountSnapshotCache must not be null");
    }

    @Override
    public AccountSnapshot getSnapshotById(Long accountId) {
        try {
            return toSnapshot(accountQueryUseCase.getAccountInfo(accountId));
        } catch (com.bank.app.account.domain.exception.AccountNotFoundException e) {
            // Translate to the published language: downstream contexts must
            // never observe account domain types, not even as exceptions.
            throw new AccountNotFoundException(accountId);
        }
    }

    @Override
    public AccountSnapshot getSnapshotByIban(String ibanValue) {
        try {
            return toSnapshot(accountQueryUseCase.getAccountInfoForTransfer(ibanValue));
        } catch (com.bank.app.account.domain.exception.AccountNotFoundException e) {
            throw new AccountNotFoundException(ibanValue);
        }
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return accountQueryUseCase.getIbansForAccounts(accountIds);
    }

    @Override
    public AccountAdjustmentResult adjustBalances(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        AccountAdjustmentResult result = adjustAccountBalancesUseCase.debitAndCredit(senderId, receiverId, amount);
        // Granular invalidation, inline (not via a helper): only the two
        // mutated accounts, only after the use case succeeds — and inline so
        // CacheInvalidationArchitectureTest can see the eviction directly.
        // Eviction deferred to AFTER_COMMIT when a transaction is active so a
        // rollback never evicts a still-valid snapshot; the else-branch keeps
        // the direct evictById call visible to CacheInvalidationArchitectureTest.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    snapshotCache.evictById(senderId);
                    snapshotCache.evictById(receiverId);
                }
            });
        } else {
            snapshotCache.evictById(senderId);
            snapshotCache.evictById(receiverId);
        }
        return result;
    }

    @Override
    public AccountAdjustmentResult reverseForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        AccountAdjustmentResult result = adjustAccountBalancesUseCase.reverseForCancellation(senderId, receiverId, amount);
        // Same AFTER_COMMIT rule as adjustBalances: commit wins, then evict.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    snapshotCache.evictById(senderId);
                    snapshotCache.evictById(receiverId);
                }
            });
        } else {
            snapshotCache.evictById(senderId);
            snapshotCache.evictById(receiverId);
        }
        return result;
    }

    private static AccountSnapshot toSnapshot(AccountInfo info) {
        return new AccountSnapshot(info.id(), info.userId(), info.currency(), info.status());
    }
}
