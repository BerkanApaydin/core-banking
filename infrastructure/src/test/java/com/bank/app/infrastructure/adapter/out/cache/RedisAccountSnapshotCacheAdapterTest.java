package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.infrastructure.adapter.in.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class RedisAccountSnapshotCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    private RedisAccountSnapshotCacheAdapter adapter;

    private static final AccountSnapshot SNAPSHOT = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        adapter = new RedisAccountSnapshotCacheAdapter(redisTemplate, new CacheProperties());
    }

    @Test
    void shouldGetById() {
        when(valueOps.get("account-snapshot:id-1")).thenReturn("1|10|TRY|ACTIVE");

        assertEquals(SNAPSHOT, adapter.getById(1L).orElseThrow());
    }

    @Test
    void shouldReturnEmptyWhenMissing() {
        when(valueOps.get("account-snapshot:id-99")).thenReturn(null);

        assertTrue(adapter.getById(99L).isEmpty());
    }

    @Test
    void shouldReturnEmptyOnCorruptData() {
        when(valueOps.get("account-snapshot:id-1")).thenReturn("not-a-snapshot");

        assertTrue(adapter.getById(1L).isEmpty());
    }

    @Test
    void shouldPutByIdWithTtl() {
        adapter.putById(1L, SNAPSHOT);

        verify(valueOps).set("account-snapshot:id-1", "1|10|TRY|ACTIVE", 60L, TimeUnit.SECONDS);
    }

    @Test
    void shouldPutByIbanAndMaintainDistributedIndex() {
        adapter.putByIban("tr33 0006 1005 1978 6456 8412 34", SNAPSHOT);

        String ibanKey = AccountSnapshotCache.ibanKey("tr33 0006 1005 1978 6456 8412 34");
        verify(valueOps).set("account-snapshot:iban-" + ibanKey, "1|10|TRY|ACTIVE", 60L, TimeUnit.SECONDS);
        verify(setOps).add("account-snapshot:idx:ibans-by-id:1", ibanKey);
        verify(redisTemplate).expire("account-snapshot:idx:ibans-by-id:1", 60L, TimeUnit.SECONDS);
        verify(valueOps).set("account-snapshot:idx:id-by-iban:" + ibanKey, "1", 60L, TimeUnit.SECONDS);
    }

    @Test
    void shouldGetByIban() {
        String ibanKey = AccountSnapshotCache.ibanKey("TR1");
        when(valueOps.get("account-snapshot:iban-" + ibanKey)).thenReturn("1|10|TRY|ACTIVE");

        assertEquals(SNAPSHOT, adapter.getByIban("tr1").orElseThrow());
    }

    @Test
    void shouldEvictByIdAcrossReplicas() {
        // Index written by another pod is still visible because it lives in Redis.
        when(setOps.members("account-snapshot:idx:ibans-by-id:1")).thenReturn(Set.of("TR1"));

        adapter.evictById(1L);

        verify(redisTemplate).delete("account-snapshot:id-1");
        verify(redisTemplate).delete("account-snapshot:iban-TR1");
        verify(redisTemplate).delete("account-snapshot:idx:id-by-iban:TR1");
        verify(redisTemplate).delete("account-snapshot:idx:ibans-by-id:1");
    }

    @Test
    void shouldEvictByIbanAndUntrackIndex() {
        when(valueOps.get("account-snapshot:idx:id-by-iban:TR1")).thenReturn("1");

        adapter.evictByIban("TR1");

        verify(redisTemplate).delete("account-snapshot:iban-TR1");
        verify(setOps).remove("account-snapshot:idx:ibans-by-id:1", "TR1");
        verify(redisTemplate).delete("account-snapshot:idx:id-by-iban:TR1");
    }

    @Test
    void shouldPutAndGetIbansBatch() {
        var ids = Set.of(1L, 2L);
        var ibans = Map.of(1L, "TR1", 2L, "TR2");
        when(valueOps.get("account-snapshot:batch:" + AccountSnapshotCache.ibansBatchKey(ids)))
                .thenReturn("1=TR1,2=TR2");

        assertEquals(ibans, adapter.getIbans(ids).orElseThrow());

        adapter.putIbans(ids, ibans);
        verify(valueOps).set(
                eq("account-snapshot:batch:" + AccountSnapshotCache.ibansBatchKey(ids)),
                eq("1=TR1,2=TR2"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldEvictAllWithKeyScan() {
        when(redisTemplate.keys("account-snapshot:*"))
                .thenReturn(Set.of("account-snapshot:id-1", "account-snapshot:iban-TR1"));

        adapter.evictAll();

        verify(redisTemplate).delete(Set.of("account-snapshot:id-1", "account-snapshot:iban-TR1"));
    }

    @Test
    void shouldKeepBatchOnGranularEvict() {
        adapter.evictIbansBatch();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldIgnoreNulls() {
        assertDoesNotThrow(() -> {
            adapter.putById(null, null);
            adapter.putByIban(null, null);
            adapter.putIbans(null, null);
            adapter.evictById(null);
            adapter.evictByIban(null);
        });
        assertTrue(adapter.getById(null).isEmpty());
        assertTrue(adapter.getByIban(null).isEmpty());
        assertTrue(adapter.getIbans(null).isEmpty());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldFailOpenWhenRedisDown() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        // Reads degrade to DB fallback instead of 500.
        assertTrue(adapter.getById(1L).isEmpty());
        assertTrue(adapter.getByIban("TR1").isEmpty());
        assertTrue(adapter.getIbans(Set.of(1L)).isEmpty());

        // Writes/evicts never propagate failures.
        assertDoesNotThrow(() -> {
            adapter.putById(1L, SNAPSHOT);
            adapter.putByIban("TR1", SNAPSHOT);
            adapter.putIbans(Set.of(1L), Map.of(1L, "TR1"));
            adapter.evictById(1L);
            adapter.evictByIban("TR1");
            adapter.evictAll();
        });
    }

    @Test
    void shouldRoundTripCodec() {
        assertEquals(SNAPSHOT, RedisAccountSnapshotCacheAdapter.decodeSnapshot(
                RedisAccountSnapshotCacheAdapter.encodeSnapshot(SNAPSHOT)));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeSnapshot(null));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeSnapshot(""));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeSnapshot("1|2|TRY"));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeSnapshot("x|y|TRY|ACTIVE"));

        var ibans = Map.of(2L, "TR2", 1L, "TR1");
        assertEquals(ibans, RedisAccountSnapshotCacheAdapter.decodeBatch(
                RedisAccountSnapshotCacheAdapter.encodeBatch(ibans)));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeBatch(null));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeBatch(""));
        assertNull(RedisAccountSnapshotCacheAdapter.decodeBatch("no-separator"));
    }

    @Test
    void shouldHonorConfiguredTtl() {
        CacheProperties props = new CacheProperties();
        props.getAccountInfo().setExpireAfterWrite(10L);
        var customTtl = new RedisAccountSnapshotCacheAdapter(redisTemplate, props);

        customTtl.putById(1L, SNAPSHOT);

        verify(valueOps).set("account-snapshot:id-1", "1|10|TRY|ACTIVE", 10L, TimeUnit.SECONDS);
    }
}
