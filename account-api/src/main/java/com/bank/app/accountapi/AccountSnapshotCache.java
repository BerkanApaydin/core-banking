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

    /**
     * Bulk id-to-IBAN read. Backends fan out to one key per account id
     * internally (never a combinatorial key over the whole set), so repeated
     * pages share entries instead of minting a distinct key per combination.
     * All-or-nothing: any missing member yields {@link Optional#empty()}.
     */
    Optional<Map<Long, String>> getIbans(Collection<Long> accountIds);

    void putIbans(Collection<Long> accountIds, Map<Long, String> ibans);

    /**
     * Operational reset: clears the whole region. Never call from request paths
     * or mutation flows — balance mutations must use {@link #evictById(Long)}
     * (O(1) per account). On a populated production cache this scans the whole
     * keyspace; schedule off-peak (see docs/operations.md).
     */
    void evictAll();

    /**
     * Removes the cached entry for a single account. Balance mutations only affect
     * the two involved accounts, so callers must prefer this over {@link #evictAll()}
     * to avoid cache stampedes on unrelated accounts.
     *
     * <p>There is intentionally no IBAN-keyed eviction: IBANs are immutable, so
     * evicting by account id (which also drops the account's IBAN snapshot
     * entries via the reverse index) covers every mutation path.
     */
    default void evictById(Long accountId) {
        evictAll();
    }

    static String ibanKey(String ibanValue) {
        Objects.requireNonNull(ibanValue, "ibanValue must not be null");
        return ibanValue.replaceAll("\\s", "").toUpperCase();
    }
}
