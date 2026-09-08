package com.bank.app.transfer.adapter.out.account;

import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountInfoCachePort;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-free in-memory fallback for {@link AccountInfoCachePort}.
 *
 * <p>Used by transfer unit tests and as {@code @ConditionalOnMissingBean}
 * fallback at runtime; production wires the infrastructure Caffeine-backed
 * adapter (primary) instead.
 */
public class InMemoryAccountInfoCacheAdapter implements AccountInfoCachePort {

    private final ConcurrentHashMap<Long, AccountAclPort.AccountInfo> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AccountAclPort.AccountInfo> byIban = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<Long, String>> ibansBatch = new ConcurrentHashMap<>();
    // Reverse index id -> normalized IBAN keys. The same AccountInfo is cached
    // under both id and IBAN keys, so evictById must drop both (see evictById).
    private final ConcurrentHashMap<Long, Set<String>> idToIbanKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ibanKeyToId = new ConcurrentHashMap<>();

    @Override
    public Optional<AccountAclPort.AccountInfo> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(accountId));
    }

    @Override
    public void putById(Long accountId, AccountAclPort.AccountInfo info) {
        if (accountId == null || info == null) {
            return;
        }
        byId.put(accountId, info);
    }

    @Override
    public Optional<AccountAclPort.AccountInfo> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIban.get(AccountInfoCachePort.ibanKey(ibanValue)));
    }

    @Override
    public void putByIban(String ibanValue, AccountAclPort.AccountInfo info) {
        if (ibanValue == null || info == null) {
            return;
        }
        String key = AccountInfoCachePort.ibanKey(ibanValue);
        byIban.put(key, info);
        ibanKeyToId.put(key, info.id());
        idToIbanKeys.computeIfAbsent(info.id(), k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    @Override
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ibansBatch.get(AccountInfoCachePort.ibansBatchKey(accountIds)));
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        ibansBatch.put(AccountInfoCachePort.ibansBatchKey(accountIds), Map.copyOf(ibans));
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
        byId.clear();
        byIban.clear();
        ibansBatch.clear();
        idToIbanKeys.clear();
        ibanKeyToId.clear();
    }

    @Override
    public void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        byId.remove(accountId);
        Set<String> ibanKeys = idToIbanKeys.remove(accountId);
        if (ibanKeys != null) {
            ibanKeys.forEach(key -> {
                byIban.remove(key);
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
        byIban.remove(key);
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
        // id->IBAN mappings are immutable; no-op by design (see Caffeine adapter).
    }
}
