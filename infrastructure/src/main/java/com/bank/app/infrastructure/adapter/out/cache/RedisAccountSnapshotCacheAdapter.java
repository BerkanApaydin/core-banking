package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.infrastructure.adapter.in.config.CacheProperties;
import com.bank.app.infrastructure.adapter.in.config.SnapshotCacheRedisCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed account snapshot cache implementation (shared across replicas).
 *
 * <p>Active when the resolved snapshot-cache backend is {@code redis}
 * (canonical {@code app.cache.account-info.backend}, legacy
 * {@code app.cache.caffeine.account-info.backend} as fallback — see
 * {@link com.bank.app.infrastructure.adapter.in.config.CacheBackendResolution}).
 * Unlike the Caffeine backend, the {@code id ↔ IBAN} reverse
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
@Conditional(SnapshotCacheRedisCondition.class)
public class RedisAccountSnapshotCacheAdapter implements AccountSnapshotCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAccountSnapshotCacheAdapter.class);

    /**
     * Single-round-trip eviction: the SMEMBERS index read and the DEL run
     * server-side, so a balance mutation costs 1 RTT per account instead of
     * read-then-delete (2 RTT). KEYS = [idKey, mapKey, idxKey],
     * ARGV = [ibanKeyPrefix, idByIbanPrefix].
     */
    private static final String LUA_EVICT_BY_ID =
        "local members = redis.call('SMEMBERS', KEYS[3])\n" +
        "local del = {KEYS[1], KEYS[2], KEYS[3]}\n" +
        "for _, m in ipairs(members) do\n" +
        "    del[#del + 1] = ARGV[1] .. m\n" +
        "    del[#del + 1] = ARGV[2] .. m\n" +
        "end\n" +
        "return redis.call('DEL', unpack(del))";

    /**
     * Single-round-trip index write: the id-to-IBAN reverse entry, its TTL,
     * and the forward lookup are applied server-side in one EVAL instead of
     * SADD + EXPIRE + SET (3 RTT). KEYS = [idxKey, idByIbanKey],
     * ARGV = [ttlSeconds, ibanKey, accountId].
     */
    private static final String LUA_TRACK_IBAN =
        "redis.call('SADD', KEYS[1], ARGV[2])\n" +
        "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
        "redis.call('SETEX', KEYS[2], ARGV[1], ARGV[3])\n" +
        "return 1";

    /**
     * Single-round-trip bulk write: N TTL'd id-to-IBAN mappings in one EVAL.
     * KEYS = map keys in ascending id order, ARGV = [ttlSeconds, value...]
     * aligned with KEYS.
     */
    private static final String LUA_PUT_IBANS =
        "local ttl = tonumber(ARGV[1])\n" +
        "for i, k in ipairs(KEYS) do\n" +
        "    redis.call('SETEX', k, ttl, ARGV[i + 1])\n" +
        "end\n" +
        "return #KEYS";


    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;
    private final int evictionBatchSize;
    private final DefaultRedisScript<Long> evictByIdScript;
    private final DefaultRedisScript<Long> putIbansScript;
    private final DefaultRedisScript<Long> trackIbanScript;

    public RedisAccountSnapshotCacheAdapter(StringRedisTemplate redisTemplate,
            CacheProperties.AccountInfoCache resolvedAccountInfoCache) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = resolvedAccountInfoCache.expireAfterWrite();
        this.evictionBatchSize = Math.toIntExact(resolvedAccountInfoCache.evictionBatchSize());
        this.evictByIdScript = new DefaultRedisScript<>(LUA_EVICT_BY_ID, Long.class);
        this.putIbansScript = new DefaultRedisScript<>(LUA_PUT_IBANS, Long.class);
        this.trackIbanScript = new DefaultRedisScript<>(LUA_TRACK_IBAN, Long.class);
    }

    @Override
    public Optional<AccountSnapshot> getById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return readSnapshot(SnapshotKeys.idKey(accountId));
    }

    @Override
    public void putById(Long accountId, AccountSnapshot snapshot) {
        if (accountId == null || snapshot == null) {
            return;
        }
        writeSnapshot(SnapshotKeys.idKey(accountId), snapshot);
    }

    @Override
    public Optional<AccountSnapshot> getByIban(String ibanValue) {
        if (ibanValue == null) {
            return Optional.empty();
        }
        return readSnapshot(SnapshotKeys.ibanKey(ibanValue));
    }

    @Override
    public void putByIban(String ibanValue, AccountSnapshot snapshot) {
        if (ibanValue == null || snapshot == null) {
            return;
        }
        String ibanKey = AccountSnapshotCache.ibanKey(ibanValue);
        writeSnapshot(SnapshotKeys.ibanKeyRaw(ibanKey), snapshot);
        trackIban(snapshot.id(), ibanKey);
    }

    @Override
    public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Optional.empty();
        }
        List<Long> ids = accountIds.stream().distinct().sorted().toList();
        if (ids.stream().anyMatch(id -> id == null)) {
            return Optional.empty();
        }
        // All-or-nothing bulk read in a single MGET round trip.
        List<String> keys = ids.stream().map(id -> SnapshotKeys.mapKey(id)).toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null || values.size() != keys.size()
                    || values.stream().anyMatch(value -> value == null || value.isEmpty())) {
                return Optional.empty();
            }
            Map<Long, String> result = new HashMap<>();
            for (int index = 0; index < ids.size(); index++) {
                result.put(ids.get(index), values.get(index));
            }
            return result.isEmpty() ? Optional.empty() : Optional.of(Map.copyOf(result));
        } catch (RuntimeException e) {
            log.warn("Redis snapshot batch read failed, falling back to DB: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        if (accountIds == null || ibans == null || ibans.isEmpty()) {
            return;
        }
        try {
            // Map keys are authoritative; one TTL'd entry per account id.
            // Sorted for a deterministic KEYS/ARGV alignment (and testability).
            List<Long> ids = ibans.keySet().stream()
                    .filter(id -> id != null && ibans.get(id) != null && !ibans.get(id).isEmpty())
                    .sorted()
                    .toList();
            if (ids.isEmpty()) {
                return;
            }
            List<String> keys = ids.stream().map(SnapshotKeys::mapKey).toList();
            Object[] args = new Object[ids.size() + 1];
            args[0] = String.valueOf(ttlSeconds);
            for (int i = 0; i < ids.size(); i++) {
                args[i + 1] = ibans.get(ids.get(i));
            }
            redisTemplate.execute(putIbansScript, keys, args);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot batch write failed: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Full-region scan + batched delete. Prefer {@link #evictById(Long)} for
     * balance mutations (O(1) Lua); reserve this for operational resets only —
     * on a populated production cache it scans the whole keyspace. Do not call
     * from request paths or mutation flows; schedule operational resets for
     * off-peak hours (see docs/operations.md).
     */
    @Override
    public void evictAll() {
        log.warn("Snapshot cache full-region evict-all started (scan batch {}). "
                + "Prefer evictById for mutations; reserve evict-all for off-peak operational resets.",
                evictionBatchSize);
        ScanOptions options = ScanOptions.scanOptions()
                .match(SnapshotKeys.scanPattern())
                .count(evictionBatchSize)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            Collection<String> batch = new ArrayList<>(evictionBatchSize);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == evictionBatchSize) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        } catch (RuntimeException e) {
            log.warn("Redis snapshot evict-all failed: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void evictById(Long accountId) {
        if (accountId == null) {
            return;
        }
        try {
            // One server-side Lua (SMEMBERS + DEL) instead of N+1 singles:
            // eviction runs inside the request path of every balance mutation.
            List<String> keys = List.of(
                    SnapshotKeys.idKey(accountId),
                    SnapshotKeys.mapKey(accountId),
                    SnapshotKeys.ibansByIdIndex(accountId));
            redisTemplate.execute(evictByIdScript, keys,
                    SnapshotKeys.ibanKeyPrefix(), SnapshotKeys.idByIbanPrefix());
        } catch (RuntimeException e) {
            log.warn("Redis snapshot evict-by-id failed: {}", e.getClass().getSimpleName());
        }
    }

    private Optional<AccountSnapshot> readSnapshot(String key) {
        try {
            return Optional.ofNullable(SnapshotCodec.decode(redisTemplate.opsForValue().get(key)));
        } catch (RuntimeException e) {
            log.warn("Redis snapshot read failed, falling back to DB: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void writeSnapshot(String key, AccountSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(key, SnapshotCodec.encode(snapshot), ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("Redis snapshot write failed: {}", e.getClass().getSimpleName());
        }
    }

    private void trackIban(Long accountId, String ibanKey) {
        try {
            String idxKey = SnapshotKeys.ibansByIdIndex(accountId);
            redisTemplate.execute(trackIbanScript, List.of(idxKey, SnapshotKeys.idByIbanIndex(ibanKey)),
                    String.valueOf(ttlSeconds), ibanKey, String.valueOf(accountId));
        } catch (RuntimeException e) {
            log.warn("Redis snapshot index write failed: {}", e.getClass().getSimpleName());
        }
    }

}
