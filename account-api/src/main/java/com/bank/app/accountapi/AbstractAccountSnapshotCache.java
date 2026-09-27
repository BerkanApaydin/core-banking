package com.bank.app.accountapi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Shared base for {@link AccountSnapshotCache} backends.
 *
 * <p>Owns canonical keys and granular invalidation. Backends identify live
 * IBAN snapshots for an account from their own storage, so expiration and
 * size eviction cannot leave an unbounded reverse index behind.
 *
 * <p>All public methods are {@code synchronized}: the storage itself is a
 * {@code ConcurrentHashMap}/Spring {@code Cache} (thread-safe for single
 * keys), but invalidation is compound (read-then-remove across the id and
 * IBAN namespaces) and must be atomic with concurrent reads/writes on the
 * same singleton instance.
 */
public abstract class AbstractAccountSnapshotCache implements AccountSnapshotCache {

    protected abstract Optional<AccountSnapshot> readSnapshot(String key);

    protected abstract void writeSnapshot(String key, AccountSnapshot snapshot);

    protected abstract void removeSnapshot(String key);

    /** Live IBAN keys only; the backend's own expiration/size policy bounds this scan. */
    protected abstract Collection<String> ibanSnapshotKeysForAccount(Long accountId);

    protected abstract Optional<Map<Long, String>> readBatch(String key);

    protected abstract void writeBatch(String key, Map<Long, String> batch);

    protected abstract void clearStorage();

    @Override
    public synchronized Optional<AccountSnapshot> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return readSnapshot("id-" + accountId);
    }

    @Override
    public synchronized void putById(Long accountId, AccountSnapshot snapshot) {
        if (accountId == null || snapshot == null) {
            return;
        }
        writeSnapshot("id-" + accountId, snapshot);
    }

    @Override
    public synchronized Optional<AccountSnapshot> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return readSnapshot("iban-" + AccountSnapshotCache.ibanKey(ibanValue));
    }

    @Override
    public synchronized void putByIban(String ibanValue, AccountSnapshot snapshot) {
        if (ibanValue == null || snapshot == null) {
            return;
        }
        String key = AccountSnapshotCache.ibanKey(ibanValue);
        writeSnapshot("iban-" + key, snapshot);
    }

    @Override
    public synchronized Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null) {
            return Optional.empty();
        }
        return readBatch(AccountSnapshotCache.ibansBatchKey(accountIds));
    }

    @Override
    public synchronized void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        writeBatch(AccountSnapshotCache.ibansBatchKey(accountIds), Map.copyOf(ibans));
    }

    @Override
    public synchronized void evictAll() {
        clearStorage();
    }

    @Override
    public synchronized void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        removeSnapshot("id-" + accountId);
        ibanSnapshotKeysForAccount(accountId).forEach(this::removeSnapshot);
    }

    @Override
    public synchronized void evictByIban(String ibanValue) {
        if (ibanValue == null) {
            return;
        }
        String key = AccountSnapshotCache.ibanKey(ibanValue);
        removeSnapshot("iban-" + key);
    }

    @Override
    public void evictIbansBatch() {
        // id->IBAN mappings are immutable (IBAN never changes on transfer), so batch
        // entries cannot go stale due to balance mutations. No-op by design; the
        // backend TTL bounds any residual staleness from out-of-band changes.
    }

}
