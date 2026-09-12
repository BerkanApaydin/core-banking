package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.infrastructure.adapter.in.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed account snapshot cache implementation (shared across replicas).
 *
 * <p>Active when {@code app.cache.caffeine.account-info.backend=redis}
 * (production). Unlike the Caffeine backend, the {@code id ↔ IBAN} reverse
 * index lives in Redis as well (sets + reverse keys), so an
 * {@code evictById} issued on one pod also drops the IBAN entries written
 * by other pods. All entries carry the same TTL as the Caffeine backend
 * ({@code expire-after-write}), so index keys can never outlive the data.
 *
 * <p>Fail-open: every Redis call is guarded — on outage reads return
 * {@code Optional.empty()} (callers fall back to the DB) and writes/evicts
 * become no-ops. A cache outage must cost extra DB load, never a 500.
 * Implements {@link AccountSnapshotCache} directly instead of extending the
 * in-memory-index base class, precisely to avoid a JVM-local index shadowing
 * the shared store.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.cache.caffeine.account-info.backend", havingValue = "redis")
public class RedisAccountSnapshotCacheAdapter implements AccountSnapshotCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAccountSnapshotCacheAdapter.class);

    static final String KEY_PREFIX = "account-snapshot:";
    static final String ID_PREFIX = "id-";
    static final String IBAN_PREFIX = "iban-";
    static final String BATCH_PREFIX = "batch:";
    static final String IDX_IBANS_BY_ID_PREFIX = "idx:ibans-by-id:";
    static final String IDX_ID_BY_IBAN_PREFIX = "idx:id-by-iban:";

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public RedisAccountSnapshotCacheAdapter(StringRedisTemplate redisTemplate,
            CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = cacheProperties.getAccountInfo().getExpireAfterWrite();
    }

    @Override
    public Optional<AccountSnapshot> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return readSnapshot(KEY_PREFIX + ID_PREFIX + accountId);
    }

    @Override
    public void putById(Long accountId, AccountSnapshot snapshot) {
        if (accountId == null || snapshot == null) {
            return;
        }
        writeSnapshot(KEY_PREFIX + ID_PREFIX + accountId, snapshot);
    }

    @Override
    public Optional<AccountSnapshot> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return readSnapshot(KEY_PREFIX + IBAN_PREFIX + AccountSnapshotCache.ibanKey(ibanValue));
    }

    @Override
    public void putByIban(String ibanValue, AccountSnapshot snapshot) {
        if (ibanValue == null || snapshot == null) {
            return;
        }
        String ibanKey = AccountSnapshotCache.ibanKey(ibanValue);
        writeSnapshot(KEY_PREFIX + IBAN_PREFIX + ibanKey, snapshot);
        trackIban(snapshot.id(), ibanKey);
    }

    @Override
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null) {
            return Optional.empty();
        }
        try {
            String raw = redisTemplate.opsForValue().get(KEY_PREFIX + BATCH_PREFIX
                    + AccountSnapshotCache.ibansBatchKey(accountIds));
            return Optional.ofNullable(decodeBatch(raw));
        } catch (RuntimeException e) {
            log.warn("Redis snapshot batch read failed, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + BATCH_PREFIX + AccountSnapshotCache.ibansBatchKey(accountIds),
                    encodeBatch(ibans), ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot batch write failed: {}", e.getMessage());
        }
    }

    @Override
    public void evictAll() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException e) {
            log.warn("Redis snapshot evict-all failed: {}", e.getMessage());
        }
    }

    @Override
    public void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + ID_PREFIX + accountId);
            String idxKey = KEY_PREFIX + IDX_IBANS_BY_ID_PREFIX + accountId;
            Set<String> ibanKeys = redisTemplate.opsForSet().members(idxKey);
            if (ibanKeys != null) {
                for (String ibanKey : ibanKeys) {
                    redisTemplate.delete(KEY_PREFIX + IBAN_PREFIX + ibanKey);
                    redisTemplate.delete(KEY_PREFIX + IDX_ID_BY_IBAN_PREFIX + ibanKey);
                }
            }
            redisTemplate.delete(idxKey);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot evict-by-id failed: {}", e.getMessage());
        }
    }

    @Override
    public void evictByIban(String ibanValue) {
        if (ibanValue == null) {
            return;
        }
        String ibanKey = AccountSnapshotCache.ibanKey(ibanValue);
        try {
            redisTemplate.delete(KEY_PREFIX + IBAN_PREFIX + ibanKey);
            String reverseKey = KEY_PREFIX + IDX_ID_BY_IBAN_PREFIX + ibanKey;
            String accountId = redisTemplate.opsForValue().get(reverseKey);
            if (accountId != null) {
                redisTemplate.opsForSet().remove(KEY_PREFIX + IDX_IBANS_BY_ID_PREFIX + accountId, ibanKey);
            }
            redisTemplate.delete(reverseKey);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot evict-by-iban failed: {}", e.getMessage());
        }
    }

    @Override
    public void evictIbansBatch() {
        // id->IBAN mappings are immutable (IBAN never changes on transfer), so batch
        // entries cannot go stale due to balance mutations. No-op by design; the
        // Redis TTL bounds any residual staleness from out-of-band changes.
    }

    private Optional<AccountSnapshot> readSnapshot(String key) {
        try {
            return Optional.ofNullable(decodeSnapshot(redisTemplate.opsForValue().get(key)));
        } catch (RuntimeException e) {
            log.warn("Redis snapshot read failed, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void writeSnapshot(String key, AccountSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(key, encodeSnapshot(snapshot), ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot write failed: {}", e.getMessage());
        }
    }

    private void trackIban(Long accountId, String ibanKey) {
        try {
            String idxKey = KEY_PREFIX + IDX_IBANS_BY_ID_PREFIX + accountId;
            redisTemplate.opsForSet().add(idxKey, ibanKey);
            redisTemplate.expire(idxKey, ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + IDX_ID_BY_IBAN_PREFIX + ibanKey,
                    String.valueOf(accountId), ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot index write failed: {}", e.getMessage());
        }
    }

    static String encodeSnapshot(AccountSnapshot snapshot) {
        return snapshot.id() + "|" + snapshot.userId() + "|" + snapshot.currency() + "|" + snapshot.status();
    }

    static AccountSnapshot decodeSnapshot(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new AccountSnapshot(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2], parts[3]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String encodeBatch(Map<Long, String> ibans) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(ibans).forEach((id, iban) -> {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(id).append('=').append(iban);
        });
        return sb.toString();
    }

    static Map<Long, String> decodeBatch(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<Long, String> result = new HashMap<>();
        for (String entry : raw.split(",", -1)) {
            int sep = entry.indexOf('=');
            if (sep <= 0) {
                return null;
            }
            try {
                result.put(Long.parseLong(entry.substring(0, sep)), entry.substring(sep + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result.isEmpty() ? null : Map.copyOf(result);
    }
}
