package com.bank.app.accountapi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared base for {@link AccountSnapshotCache} backends.
 *
 * <p>Owns everything except raw key/value storage: the {@code id ↔ IBAN}
 * reverse index (so {@code evictById} also drops the IBAN entries holding the
 * same mutable-status snapshot), canonical batch keys, and granular
 * invalidation. Subclasses only provide four storage primitives, which keeps
 * the Caffeine (infrastructure) and in-memory (fallback) backends free of
 * copy-pasted invalidation logic.
 */
public abstract class AbstractAccountSnapshotCache implements AccountSnapshotCache {

    // Ephemeral reverse index id -> normalized IBAN keys so evictById can also
    // drop the IBAN entries holding the same (mutable-status) snapshot.
    // Rebuilt lazily on putByIban/putIbans; a restart may miss an IBAN evict,
    // bounded by the backend TTL. Never a source of truth, only invalidation.
    private final ConcurrentHashMap<Long, Set<String>> idToIbanKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ibanKeyToId = new ConcurrentHashMap<>();

    protected abstract Optional<AccountSnapshot> readSnapshot(String key);

    protected abstract void writeSnapshot(String key, AccountSnapshot snapshot);

    protected abstract void removeSnapshot(String key);

    protected abstract Optional<Map<Long, String>> readBatch(String key);

    protected abstract void writeBatch(String key, Map<Long, String> batch);

    protected abstract void clearStorage();

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
        trackIban(snapshot.id(), key);
    }

    @Override
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null) {
            return Optional.empty();
        }
        return readBatch(AccountSnapshotCache.ibansBatchKey(accountIds));
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        writeBatch(AccountSnapshotCache.ibansBatchKey(accountIds), Map.copyOf(ibans));
    }

    @Override
    public void evictAll() {
        clearStorage();
        idToIbanKeys.clear();
        ibanKeyToId.clear();
    }

    @Override
    public void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        removeSnapshot("id-" + accountId);
        Set<String> ibanKeys = idToIbanKeys.remove(accountId);
        if (ibanKeys != null) {
            ibanKeys.forEach(key -> {
                removeSnapshot("iban-" + key);
                ibanKeyToId.remove(key);
            });
        }
    }

    @Override
    public void evictByIban(String ibanValue) {
        if (ibanValue == null) {
            return;
        }
        String key = AccountSnapshotCache.ibanKey(ibanValue);
        removeSnapshot("iban-" + key);
        Long accountId = ibanKeyToId.remove(key);
        if (accountId != null) {
            Set<String> keys = idToIbanKeys.get(accountId);
            if (keys != null) {
                keys.remove(key);
            }
        }
    }

    @Override
    public void evictIbansBatch() {
        // id->IBAN mappings are immutable (IBAN never changes on transfer), so batch
        // entries cannot go stale due to balance mutations. No-op by design; the
        // backend TTL bounds any residual staleness from out-of-band changes.
    }

    private void trackIban(Long accountId, String ibanKey) {
        ibanKeyToId.put(ibanKey, accountId);
        idToIbanKeys.computeIfAbsent(accountId, k -> ConcurrentHashMap.newKeySet()).add(ibanKey);
    }
}
