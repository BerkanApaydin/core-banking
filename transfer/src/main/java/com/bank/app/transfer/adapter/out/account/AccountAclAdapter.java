package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountAclPort;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Anti-corruption adapter: implements transfer's {@link AccountAclPort} by
 * delegating to the Account context's published language ({@link AccountApi}).
 * This module depends only on {@code account-api}, never on the
 * {@code account} module itself.
 *
 * <p>Caching goes through {@link AccountSnapshotCache} (infrastructure owns the
 * backend) instead of Spring-Cache annotations, so this adapter stays
 * framework-free. Reads populate the cache; invalidation is owned by the
 * Account boundary itself ({@code AccountApiAdapter} evicts mutated snapshots
 * before returning), so this adapter must not evict — a second eviction here
 * would only double the mutation-path Redis round trips.
 */
public class AccountAclAdapter implements AccountAclPort {

    private final AccountApi accountApi;
    private final AccountSnapshotCache cache;

    /**
     * Singleflight guards: one flight per cache key collapses a thundering herd
     * (N threads missing the same TTL window) into one backend read. Locks are
     * striped by key and removed after use; the map never grows beyond the
     * number of concurrently missing keys.
     *
     * <p>Implemented with a shared future rather than a lock object: the
     * leader completes the future and every waiter observes the same result
     * (or the same failure). The earlier lock-object version removed the
     * lock from the map while still holding it, so a third caller could
     * start a second flight for the same key while a second caller was still
     * waiting on the first — two concurrent database reads for one key.
     */
    private final ConcurrentHashMap<String, CompletableFuture<AccountSnapshot>> inflight = new ConcurrentHashMap<>();

    public AccountAclAdapter(AccountApi accountApi, AccountSnapshotCache cache) {
        this.accountApi = Objects.requireNonNull(accountApi, "AccountApi must not be null");
        this.cache = Objects.requireNonNull(cache, "AccountSnapshotCache must not be null");
    }

    /**
     * Runs {@code loader} once per key while a flight for that key is in
     * progress; concurrent callers share its outcome. The original failure
     * is rethrown unwrapped, so callers keep seeing the published-language
     * exception type (e.g. AccountNotFoundException) rather than a
     * CompletionException wrapper.
     */
    private AccountSnapshot singleflight(String key, Supplier<AccountSnapshot> loader) {
        CompletableFuture<AccountSnapshot> mine = new CompletableFuture<>();
        CompletableFuture<AccountSnapshot> running = inflight.putIfAbsent(key, mine);
        if (running != null) {
            return await(running);
        }
        try {
            AccountSnapshot value = loader.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error failure) {
            mine.completeExceptionally(failure);
            throw failure;
        } finally {
            // Removed only after the outcome is published: a caller that
            // arrives after this point starts a fresh flight, a caller that
            // arrived before it is already waiting on `mine`.
            inflight.remove(key, mine);
        }
    }

    private static AccountSnapshot await(CompletableFuture<AccountSnapshot> flight) {
        try {
            return flight.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an in-flight account lookup",
                    interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("In-flight account lookup failed", cause);
        }
    }

    @Override
    public AccountAclPort.AccountInfo getAccountInfo(AccountId accountId) {
        // ACL edge: the published language speaks Long; the transfer side
        // speaks AccountId. Translation happens exactly here, nowhere else.
        Long rawId = accountId.value();
        AccountSnapshot snapshot = cache.getById(rawId)
                .orElseGet(() -> singleflight("id-" + rawId,
                        () -> cache.getById(rawId).orElseGet(() -> {
                            AccountSnapshot fresh = accountApi.getSnapshotById(rawId);
                            cache.putById(rawId, fresh);
                            return fresh;
                        })));
        return toAccountInfo(snapshot);
    }

    @Override
    public AccountAclPort.AccountInfo getAccountInfoForTransfer(String ibanValue) {
        AccountSnapshot snapshot = cache.getByIban(ibanValue)
                .orElseGet(() -> singleflight("iban-" + AccountSnapshotCache.ibanKey(ibanValue),
                        () -> cache.getByIban(ibanValue).orElseGet(() -> {
                            AccountSnapshot fresh = accountApi.getSnapshotByIban(ibanValue);
                            cache.putByIban(ibanValue, fresh);
                            return fresh;
                        })));
        return toAccountInfo(snapshot);
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
    public AccountAclPort.MutationResult debitAndCredit(AccountId senderId, AccountId receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        AccountAdjustmentResult result = accountApi.adjustBalances(
                senderId.value(), receiverId.value(), amount);
        return toMutationResult(result);
    }

    @Override
    public AccountAclPort.MutationResult reverseBalancesForCancellation(AccountId senderId, AccountId receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        AccountAdjustmentResult result = accountApi.reverseForCancellation(
                senderId.value(), receiverId.value(), amount);
        return toMutationResult(result);
    }

    private static AccountAclPort.AccountInfo toAccountInfo(AccountSnapshot snapshot) {
        return new AccountAclPort.AccountInfo(
                new AccountId(snapshot.id()), snapshot.userId(), snapshot.currency(), snapshot.status());
    }

    private static AccountAclPort.MutationResult toMutationResult(AccountAdjustmentResult result) {
        return new AccountAclPort.MutationResult(
                new AccountId(result.senderAccountId()),
                new AccountId(result.receiverAccountId()),
                result.senderNewBalance(),
                result.receiverNewBalance());
    }
}
