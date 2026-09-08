package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountInfoCachePort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine-backed {@link AccountInfoCachePort} implementation.
 *
 * <p>Reuses the {@code accountAclInfo} cache region defined in
 * {@code CacheConfig}. Takes precedence over the transfer module's in-memory
 * fallback via {@code @Primary}.
 */
@Component
@Primary
public class CaffeineAccountInfoCacheAdapter implements AccountInfoCachePort {

    private static final String CACHE_NAME = "accountAclInfo";

    private final CacheManager cacheManager;
    // Ephemeral reverse index id -> normalized IBAN keys so evictById can also
    // drop the IBAN entries holding the same (mutable-status) AccountInfo.
    // Rebuilt lazily on putByIban; a restart may miss an IBAN evict, bounded by
    // the 60s TTL. Never used as a source of truth, only for invalidation.
    private final ConcurrentHashMap<Long, Set<String>> idToIbanKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ibanKeyToId = new ConcurrentHashMap<>();

    public CaffeineAccountInfoCacheAdapter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CACHE_NAME);
        }
        return cache;
    }

    @Override
    public Optional<AccountAclPort.AccountInfo> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache().get("id-" + accountId, AccountAclPort.AccountInfo.class));
    }

    @Override
    public void putById(Long accountId, AccountAclPort.AccountInfo info) {
        if (accountId == null || info == null) {
            return;
        }
        cache().put("id-" + accountId, info);
    }

    @Override
    public Optional<AccountAclPort.AccountInfo> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                cache().get("iban-" + AccountInfoCachePort.ibanKey(ibanValue), AccountAclPort.AccountInfo.class));
    }

    @Override
    public void putByIban(String ibanValue, AccountAclPort.AccountInfo info) {
        if (ibanValue == null || info == null) {
            return;
        }
        String key = AccountInfoCachePort.ibanKey(ibanValue);
        cache().put("iban-" + key, info);
        ibanKeyToId.put(key, info.id());
        idToIbanKeys.computeIfAbsent(info.id(), k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache().get(AccountInfoCachePort.ibansBatchKey(accountIds), Map.class));
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        cache().put(AccountInfoCachePort.ibansBatchKey(accountIds), Map.copyOf(ibans));
        // Feed the id->IBAN reverse index from batch data (IBANs are immutable):
        // narrows the post-restart window where evictById cannot find IBAN keys.
        ibans.forEach((id, iban) -> {
            if (id != null && iban != null) {
                String key = AccountInfoCachePort.ibanKey(iban);
                ibanKeyToId.put(key, id);
                idToIbanKeys.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(key);
            }
        });
    }

    @Override
    public void evictAll() {
        cache().clear();
        idToIbanKeys.clear();
        ibanKeyToId.clear();
    }

    @Override
    public void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        cache().evict("id-" + accountId);
        Set<String> ibanKeys = idToIbanKeys.remove(accountId);
        if (ibanKeys != null) {
            ibanKeys.forEach(key -> {
                cache().evict("iban-" + key);
                ibanKeyToId.remove(key);
            });
        }
    }

    @Override
    public void evictByIban(String ibanValue) {
        if (ibanValue == null) {
            return;
        }
        String key = AccountInfoCachePort.ibanKey(ibanValue);
        cache().evict("iban-" + key);
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
        // entries cannot go stale due to balance mutations. No-op by design; TTL (60s)
        // bounds any residual staleness from out-of-band account changes.
    }
}
