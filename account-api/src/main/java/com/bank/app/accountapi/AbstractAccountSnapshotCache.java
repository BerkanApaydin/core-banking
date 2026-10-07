package com.bank.app.accountapi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared base for {@link AccountSnapshotCache} backends.
 *
 * <p>Owns canonical keys, per-id IBAN mappings and granular invalidation.
 * Bulk reads fan out to one mapping per account id — never a combinatorial
 * key over the whole id set — so repeated pages share entries instead of
 * minting a distinct cache key per page combination.
 *
 * <p>Threading: single-key reads and writes delegate straight to the backend
 * ({@code ConcurrentHashMap}, Caffeine and Redis are all thread-safe for
 * single keys), so reads never block each other. Only the compound
 * invalidations (read-then-remove across the id, IBAN and mapping namespaces)
 * synchronize on the instance.
 */
public abstract class AbstractAccountSnapshotCache implements AccountSnapshotCache {

    protected abstract Optional<AccountSnapshot> readSnapshot(String key);

    protected abstract void writeSnapshot(String key, AccountSnapshot snapshot);

    protected abstract void removeSnapshot(String key);

    /** Single id-to-IBAN mapping (one key per account id). */
    protected abstract Optional<String> readIbanMapping(Long accountId);

    protected abstract void writeIbanMapping(Long accountId, String iban);

    protected abstract void removeIbanMapping(Long accountId);

    /**
     * Clears snapshots AND id-to-IBAN mappings. Backends that share one
     * storage region for both satisfy this with a single clear.
     */
    protected abstract void clearStorage();

    /**
     * Reverse index: account id to live {@code "iban-*"} snapshot keys.
     * Makes {@link #evictById} O(1) instead of an O(n) storage scan per
     * balance mutation. Entries are tiny (one IBAN key string per cached
     * account) and bounded by distinct cached accounts; TTL-expired snapshots
     * leave harmless dangling keys that the next eviction of that account
     * removes. Cleared wholesale by {@link #evictAll()}.
     */
    private final ConcurrentHashMap<Long, Set<String>> ibanKeysByAccount = new ConcurrentHashMap<>();

    @Override
    public Optional<AccountSnapshot> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return readSnapshot("id-" + accountId);
    }

    @Override
    public void putById(Long accountId, AccountSnapshot snapshot) {
        if (accountId == null || snapshot == null) {
            return;
        }
        writeSnapshot("id-" + accountId, snapshot);
    }

    @Override
    public Optional<AccountSnapshot> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return readSnapshot("iban-" + AccountSnapshotCache.ibanKey(ibanValue));
    }

    @Override
    public void putByIban(String ibanValue, AccountSnapshot snapshot) {
        if (ibanValue == null || snapshot == null) {
            return;
        }
        String key = AccountSnapshotCache.ibanKey(ibanValue);
        writeSnapshot("iban-" + key, snapshot);
        if (snapshot.id() != null) {
            ibanKeysByAccount.computeIfAbsent(snapshot.id(), id -> ConcurrentHashMap.newKeySet())
                    .add("iban-" + key);
        }
    }

    @Override
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Optional.empty();
        }
        // All-or-nothing: a partially cached set is a miss, so callers fall
        // back to one batched DB read instead of N single reads. A null
        // member counts as a miss (previously sorting threw on it).
        if (accountIds.stream().anyMatch(id -> id == null)) {
            return Optional.empty();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : new TreeSet<>(accountIds)) {
            Optional<String> iban = readIbanMapping(id);
            if (iban.isEmpty() || iban.get().isEmpty()) {
                return Optional.empty();
            }
            result.put(id, iban.get());
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(Map.copyOf(result));
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        // Map keys are authoritative; the collection only guards the null contract.
        ibans.forEach((id, iban) -> {
            if (id != null && iban != null && !iban.isEmpty()) {
                writeIbanMapping(id, iban);
            }
        });
    }

    @Override
    public synchronized void evictAll() {
        clearStorage();
        ibanKeysByAccount.clear();
    }

    @Override
    public synchronized void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        removeSnapshot("id-" + accountId);
        Set<String> ibanKeys = ibanKeysByAccount.remove(accountId);
        if (ibanKeys != null) {
            ibanKeys.forEach(this::removeSnapshot);
        }
        removeIbanMapping(accountId);
    }
}
