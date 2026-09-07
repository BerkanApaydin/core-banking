package com.bank.app.transfer.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache boundary for account reads at the transfer ACL edge.
 *
 * <p>Keeps Spring-Cache (or any caching technology) out of the transfer module:
 * the ACL adapter programs against this port, infrastructure provides the
 * Caffeine/Redis-backed implementation. Mutations evict through this port so
 * subsequent reads never observe pre-transaction values.
 */
public interface AccountInfoCachePort {

    Optional<AccountAclPort.AccountInfo> getById(Long accountId);

    void putById(Long accountId, AccountAclPort.AccountInfo info);

    Optional<AccountAclPort.AccountInfo> getByIban(String ibanValue);

    void putByIban(String ibanValue, AccountAclPort.AccountInfo info);

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
     * {@code AccountInfo} (including mutable {@code status}) is cached under
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
}
