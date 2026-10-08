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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @Mock
    private Cursor<String> cursor;

    private RedisAccountSnapshotCacheAdapter adapter;

    private static final AccountSnapshot SNAPSHOT = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        adapter = new RedisAccountSnapshotCacheAdapter(redisTemplate, new CacheProperties(null));
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
        adapter.putByIban("tr45 0006 1005 1978 6456 8412 34", SNAPSHOT);

        String ibanKey = AccountSnapshotCache.ibanKey("tr45 0006 1005 1978 6456 8412 34");
        verify(valueOps).set("account-snapshot:iban-" + ibanKey, "1|10|TRY|ACTIVE", 60L, TimeUnit.SECONDS);
        // Single server-side EVAL (SADD + EXPIRE + SETEX): 1 RTT instead of 3.
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of(
                        "account-snapshot:idx:ibans-by-id:1",
                        "account-snapshot:idx:id-by-iban:" + ibanKey)),
                eq("60"), eq(ibanKey), eq("1"));
        verify(setOps, never()).add(anyString(), anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
        verify(valueOps, never()).set(anyString(), eq("1"), anyLong(), any());
    }

    @Test
    void shouldGetByIban() {
        String ibanKey = AccountSnapshotCache.ibanKey("TR1");
        when(valueOps.get("account-snapshot:iban-" + ibanKey)).thenReturn("1|10|TRY|ACTIVE");

        assertEquals(SNAPSHOT, adapter.getByIban("tr1").orElseThrow());
    }

    @Test
    void shouldEvictByIdInSingleLuaRoundTrip() {
        // Index written by another pod is still visible because SMEMBERS runs
        // server-side inside the Lua script (no client-side read-then-delete).
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(5L);

        adapter.evictById(1L);

        // One EVAL with the base keys + key prefixes: 1 round trip on the
        // mutation hot path instead of SMEMBERS + DEL.
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of(
                        "account-snapshot:id-1",
                        "account-snapshot:iban-of:1",
                        "account-snapshot:idx:ibans-by-id:1")),
                eq("account-snapshot:iban-"),
                eq("account-snapshot:idx:id-by-iban:"));
        verify(redisTemplate, never()).delete(anyList());
    }

    @Test
    void shouldPutAndGetIbansBatchPerId() {
        var ids = Set.of(1L, 2L);
        var ibans = Map.of(1L, "TR1", 2L, "TR2");
        when(valueOps.multiGet(List.of("account-snapshot:iban-of:1", "account-snapshot:iban-of:2")))
                .thenReturn(List.of("TR1", "TR2"));

        assertEquals(ibans, adapter.getIbans(ids).orElseThrow());

        adapter.putIbans(ids, ibans);
        // One EVAL with KEYS/ARGV aligned in ascending id order (TTL + values).
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("account-snapshot:iban-of:1", "account-snapshot:iban-of:2")),
                eq("60"), eq("TR1"), eq("TR2"));
    }

    @Test
    void shouldMissBulkReadOnPartialHit() {
        when(valueOps.multiGet(List.of("account-snapshot:iban-of:1", "account-snapshot:iban-of:2")))
                .thenReturn(Arrays.asList("TR1", null));

        assertTrue(adapter.getIbans(Set.of(1L, 2L)).isEmpty());
    }

    @Test
    void shouldEvictAllWithKeyScan() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("account-snapshot:id-1", "account-snapshot:iban-TR1");

        adapter.evictAll();

        verify(redisTemplate).delete(List.of("account-snapshot:id-1", "account-snapshot:iban-TR1"));
        verify(redisTemplate, never()).keys(anyString());
        verify(cursor).close();
    }

    @Test
    void shouldDropIbanMappingOnEvictById() {
        adapter.evictById(1L);

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of(
                        "account-snapshot:id-1",
                        "account-snapshot:iban-of:1",
                        "account-snapshot:idx:ibans-by-id:1")),
                eq("account-snapshot:iban-"),
                eq("account-snapshot:idx:id-by-iban:"));
    }

    @Test
    void shouldIgnoreNulls() {
        assertDoesNotThrow(() -> {
            adapter.putById(null, null);
            adapter.putByIban(null, null);
            adapter.putIbans(null, null);
            adapter.evictById(null);
        });
        assertTrue(adapter.getById(null).isEmpty());
        assertTrue(adapter.getByIban(null).isEmpty());
        assertTrue(adapter.getIbans(null).isEmpty());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldFailOpenWhenRedisDown() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));
        when(valueOps.multiGet(any())).thenThrow(new RuntimeException("connection refused"));

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
            adapter.evictAll();
        });
    }

    @Test
    void shouldRoundTripCodec() {
        // S4: wire format lives in SnapshotCodec; the adapter only calls it.
        assertEquals(SNAPSHOT, SnapshotCodec.decode(SnapshotCodec.encode(SNAPSHOT)));
        assertNull(SnapshotCodec.decode(null));
        assertNull(SnapshotCodec.decode(""));
        assertNull(SnapshotCodec.decode("1|2|TRY"));
        assertNull(SnapshotCodec.decode("x|y|TRY|ACTIVE"));
    }

    @Test
    void shouldHonorConfiguredTtl() {
        CacheProperties props = new CacheProperties(
                new CacheProperties.AccountInfoCache("caffeine", 1000, 10L, 500L));
        var customTtl = new RedisAccountSnapshotCacheAdapter(redisTemplate, props);

        customTtl.putById(1L, SNAPSHOT);

        verify(valueOps).set("account-snapshot:id-1", "1|10|TRY|ACTIVE", 10L, TimeUnit.SECONDS);
    }
}
