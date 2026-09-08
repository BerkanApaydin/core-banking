package com.bank.app.accountapi;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache boundary for account reads at context edges.
 *
 * <p>Part of the Account published language: downstream contexts (e.g. transfer)
 * program their anti-corruption adapters against this port, and infrastructure
 * provides the backend. Keeping the contract here — instead of in a consuming
 * context — is what lets {@code infrastructure} stay compile-independent of
 * downstream bounded contexts.
 *
 * <p>Keeps caching technology out of consuming modules: the ACL adapter programs
 * against this port, infrastructure provides the Caffeine/Redis-backed
 * implementation. Mutations evict through this port so subsequent reads never
 * observe pre-transaction values.
 */
public interface AccountSnapshotCache {

    Optional<AccountSnapshot> getById(Long accountId);

    void putById(Long accountId, AccountSnapshot snapshot);

    Optional<AccountSnapshot> getByIban(String ibanValue);

    void putByIban(String ibanValue, AccountSnapshot snapshot);

    Optional<Map<Long, String>> getIbans(Collection<Long> accountIds);

    void putIbans(Collection<Long> accountIds, Map<Long, String> ibans);

    void evictAll();

    /**
     * Removes the cached entry for a single account. Balance mutations only affect
     * the two involved accounts, so callers must prefer this over {@link #evictAll()}
     * to avoid cache stampedes on unrelated accounts.
     */
    default void evictById(Long accountId) {
        evictAll();
    }

    /**
     * Removes the cached entry for a single IBAN. Required because the same
     * {@code AccountSnapshot} (including mutable {@code status}) is cached under
     * both {@code id-*} and {@code iban-*} keys: evicting only the id entry
     * would leave the IBAN entry serving stale status (e.g. ACTIVE after a
     * suspend) until TTL expiry.
     */
    default void evictByIban(String ibanValue) {
        evictAll();
    }

    /**
     * Drops cached IBAN-batch lookups which may reference mutated accounts.
     * Batch keys aggregate multiple IDs, so they cannot be invalidated by single ID.
     */
    default void evictIbansBatch() {
        evictAll();
    }

    static String ibanKey(String ibanValue) {
        Objects.requireNonNull(ibanValue, "ibanValue must not be null");
        return ibanValue.replaceAll("\\s", "").toUpperCase();
    }

    /**
     * Order-insensitive batch key: callers build the ID set with unordered
     * collections (e.g. {@code Collectors.toSet()}), so the raw
     * {@code collection.toString()} would miss on identical sets in different
     * order. Sorting first keeps the key canonical.
     */
    static String ibansBatchKey(Collection<Long> accountIds) {
        Objects.requireNonNull(accountIds, "accountIds must not be null");
        return accountIds.stream().sorted().toList().toString();
    }
}
