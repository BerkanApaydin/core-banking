package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.transfer.application.port.out.AccountAclPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

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

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getCache("accountAclInfo")).thenReturn(cache);
        adapter = new CaffeineAccountInfoCacheAdapter(cacheManager);
    }

    @Test
    void shouldGetById() {
        var info = new AccountAclPort.AccountInfo(1L, 10L, "TRY", "ACTIVE");
        when(cache.get("id-1", AccountAclPort.AccountInfo.class)).thenReturn(info);

        assertEquals(info, adapter.getById(1L).orElseThrow());
    }

    @Test
    void shouldReturnEmptyWhenCacheMissing() {
        when(cacheManager.getCache("accountAclInfo")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> adapter.getById(1L));
    }

    @Test
    void shouldPutAndEvict() {
        var info = new AccountAclPort.AccountInfo(1L, 10L, "TRY", "ACTIVE");

        adapter.putById(1L, info);
        verify(cache).put("id-1", info);

        adapter.putByIban("tr1", info);
        verify(cache).put("iban-TR1", info);

        adapter.putIbans(Set.of(1L), Map.of(1L, "TR1"));
        verify(cache).put(eq(Set.of(1L).toString()), anyMap());

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
        adapter.evictById(1L);

        verify(cache).evict("id-1");
        verify(cache, never()).clear();
    }

    @Test
    void shouldEvictIbanEntryWhenEvictingById() {
        var info = new AccountAclPort.AccountInfo(1L, 10L, "TRY", "ACTIVE");
        adapter.putByIban("TR330006100519786456841234", info);

        adapter.evictById(1L);

        verify(cache).evict("id-1");
        verify(cache).evict("iban-TR330006100519786456841234");
        verify(cache, never()).clear();
    }

    @Test
    void shouldEvictSingleIbanEntry() {
        var info = new AccountAclPort.AccountInfo(1L, 10L, "TRY", "ACTIVE");
        adapter.putByIban("TR330006100519786456841234", info);

        adapter.evictByIban("TR330006100519786456841234");

        verify(cache).evict("iban-TR330006100519786456841234");
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
