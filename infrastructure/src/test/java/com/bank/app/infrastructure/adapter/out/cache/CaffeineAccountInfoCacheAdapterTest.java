package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CaffeineAccountInfoCacheAdapterTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private CaffeineAccountInfoCacheAdapter adapter;

    private CaffeineCache useRealCache() {
        var realCache = new CaffeineCache("accountAclInfo", Caffeine.newBuilder().maximumSize(2).build());
        when(cacheManager.getCache("accountAclInfo")).thenReturn(realCache);
        return realCache;
    }

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getCache("accountAclInfo")).thenReturn(cache);
        adapter = new CaffeineAccountInfoCacheAdapter(cacheManager);
    }

    @Test
    void shouldGetById() {
        var snapshot = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        when(cache.get("id-1", AccountSnapshot.class)).thenReturn(snapshot);

        assertEquals(snapshot, adapter.getById(1L).orElseThrow());
    }

    @Test
    void shouldReturnEmptyWhenCacheMissing() {
        when(cacheManager.getCache("accountAclInfo")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> adapter.getById(1L));
    }

    @Test
    void shouldPutAndEvict() {
        var snapshot = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");

        adapter.putById(1L, snapshot);
        verify(cache).put("id-1", snapshot);

        adapter.putByIban("tr1", snapshot);
        verify(cache).put("iban-TR1", snapshot);

        adapter.putIbans(Set.of(1L), Map.of(1L, "TR1"));
        verify(cache).put(AccountSnapshotCache.ibansBatchKey(Set.of(1L)), Map.of(1L, "TR1"));

        adapter.evictAll();
        verify(cache).clear();
    }

    @Test
    void shouldIgnoreNulls() {
        assertDoesNotThrow(() -> {
            adapter.putById(null, null);
            adapter.putByIban(null, null);
            adapter.putIbans(null, null);
        });
        assertTrue(adapter.getById(null).isEmpty());
        assertTrue(adapter.getByIban(null).isEmpty());
        assertTrue(adapter.getIbans(null).isEmpty());
    }

    @Test
    void shouldEvictSingleAccountWithoutClearingUnrelatedEntries() {
        var realCache = useRealCache();
        adapter.putById(1L, new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));
        adapter.putById(2L, new AccountSnapshot(2L, 20L, "TRY", "ACTIVE"));

        adapter.evictById(1L);

        assertTrue(adapter.getById(1L).isEmpty());
        assertTrue(adapter.getById(2L).isPresent());
        assertEquals(1, realCache.getNativeCache().estimatedSize());
    }

    @Test
    void shouldEvictIbanEntryWhenEvictingById() {
        useRealCache();
        var snapshot = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        adapter.putByIban("TR450006100519786456841234", snapshot);

        adapter.evictById(1L);

        assertTrue(adapter.getByIban("TR450006100519786456841234").isEmpty());
    }

    @Test
    void sizeEvictionDoesNotLeaveUnboundedReverseIndex() {
        var realCache = useRealCache();
        for (int accountId = 1; accountId <= 100; accountId++) {
            adapter.putByIban("TR" + accountId,
                    new AccountSnapshot((long) accountId, 10L, "TRY", "ACTIVE"));
        }
        realCache.getNativeCache().cleanUp();

        assertTrue(realCache.getNativeCache().estimatedSize() <= 2);
        adapter.evictById(1L);
        assertTrue(realCache.getNativeCache().estimatedSize() <= 2);
    }

    @Test
    void shouldEvictSingleIbanEntry() {
        var snapshot = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        adapter.putByIban("TR450006100519786456841234", snapshot);

        adapter.evictByIban("TR450006100519786456841234");

        verify(cache).evict("iban-TR450006100519786456841234");
        verify(cache, never()).evict("id-1");
        verify(cache, never()).clear();
    }

    @Test
    void shouldIgnoreNullIdOnGranularEvict() {
        assertDoesNotThrow(() -> {
            adapter.evictById(null);
            adapter.evictByIban(null);
        });

        verify(cache, never()).evict(anyString());
        verify(cache, never()).clear();
    }
}
