package com.bank.app.transfer.adapter.out.account;

import com.bank.app.account.api.AccountAdjustmentResult;
import com.bank.app.account.api.AccountApi;
import com.bank.app.account.api.AccountSnapshot;
import com.bank.app.account.api.AccountSnapshotCache;
import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountAclPort;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    private static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofSeconds(10);

    private final AccountApi accountApi;
    private final AccountSnapshotCache cache;
    private final Duration awaitTimeout;

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
    private final ConcurrentHashMap<String, CompletableFuture<?>> inflight = new ConcurrentHashMap<>();

    public AccountAclAdapter(AccountApi accountApi, AccountSnapshotCache cache) {
        this(accountApi, cache, DEFAULT_AWAIT_TIMEOUT);
    }

    AccountAclAdapter(AccountApi accountApi, AccountSnapshotCache cache, Duration awaitTimeout) {
        this.accountApi = Objects.requireNonNull(accountApi, "AccountApi must not be null");
        this.cache = Objects.requireNonNull(cache, "AccountSnapshotCache must not be null");
        this.awaitTimeout = Objects.requireNonNull(awaitTimeout, "awaitTimeout must not be null");
        if (awaitTimeout.isZero() || awaitTimeout.isNegative()) {
            throw new IllegalArgumentException("awaitTimeout must be positive");
        }
    }

    /**
     * Runs {@code loader} once per key while a flight for that key is in
     * progress; concurrent callers share its outcome. The original failure
     * is rethrown unwrapped, so callers keep seeing the published-language
     * exception type (e.g. AccountNotFoundException) rather than a
     * CompletionException wrapper.
     */
    @SuppressWarnings("unchecked")
    private <T> T singleflight(String key, Supplier<T> loader) {
        CompletableFuture<T> mine = new CompletableFuture<>();
        CompletableFuture<?> running = inflight.putIfAbsent(key, mine);
        if (running != null) {
            return await((CompletableFuture<T>) running, awaitTimeout);
        }
        try {
            T value = loader.get();
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

    private static <T> T await(CompletableFuture<T> flight, Duration timeout) {
        try {
            return flight.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an in-flight account lookup",
                    interrupted);
        } catch (TimeoutException timedOut) {
            throw new IllegalStateException(
                    "Timed out after " + timeout.toMillis()
                    + " ms waiting for an in-flight account lookup",
                    timedOut);
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
        Objects.requireNonNull(accountIds, "Account IDs must not be null");
        return cache.getIbans(accountIds)
                .orElseGet(() -> singleflight(ibansFlightKey(accountIds),
                        () -> cache.getIbans(accountIds).orElseGet(() -> {
                            Map<Long, String> ibans = accountApi.getIbansForAccounts(accountIds);
                            cache.putIbans(accountIds, ibans);
                            return ibans;
                        })));
    }

    private static String ibansFlightKey(Collection<Long> accountIds) {
        // Order-insensitive: callers that pass the same set in a different
        // order must still collapse onto one backend read. Nulls are ignored
        // here only for key construction; the cache/API layer keeps the
        // original contract.
        return "ibans-" + accountIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
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
