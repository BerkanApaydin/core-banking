package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAccountInfoCacheAdapterTest {

    private InMemoryAccountInfoCacheAdapter cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryAccountInfoCacheAdapter();
    }

    @Test
    void shouldMissThenHitById() {
        assertTrue(cache.getById(1L).isEmpty());

        var info = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        cache.putById(1L, info);

        assertEquals(info, cache.getById(1L).orElseThrow());
    }

    @Test
    void shouldNormalizeIbanKeys() {
        var info = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        cache.putByIban("tr33 0006 1005 1978 6456 8412 34", info);

        assertEquals(info, cache.getByIban("TR330006100519786456841234").orElseThrow());
    }

    @Test
    void shouldCacheIbansBatch() {
        var ids = Set.of(1L, 2L);
        assertTrue(cache.getIbans(ids).isEmpty());

        var ibans = Map.of(1L, "TR1", 2L, "TR2");
        cache.putIbans(ids, ibans);

        assertEquals(ibans, cache.getIbans(ids).orElseThrow());
    }

    @Test
    void shouldEvictAll() {
        cache.putById(1L, new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));
        cache.putByIban("TR1", new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));
        cache.putIbans(Set.of(1L), Map.of(1L, "TR1"));

        cache.evictAll();

        assertTrue(cache.getById(1L).isEmpty());
        assertTrue(cache.getByIban("TR1").isEmpty());
        assertTrue(cache.getIbans(Set.of(1L)).isEmpty());
    }

    @Test
    void shouldEvictIbanEntryWhenEvictingById() {
        var info = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        cache.putById(1L, info);
        cache.putByIban("TR330006100519786456841234", info);
        cache.putById(99L, new AccountSnapshot(99L, 30L, "TRY", "ACTIVE"));

        cache.evictById(1L);

        assertTrue(cache.getById(1L).isEmpty(), "id entry must be evicted");
        assertTrue(cache.getByIban("TR330006100519786456841234").isEmpty(),
                "IBAN entry for the same account must be evicted (status is mutable)");
        assertTrue(cache.getById(99L).isPresent(), "unrelated account must stay cached");
    }

    @Test
    void shouldEvictSingleIbanEntry() {
        var info1 = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
        var info2 = new AccountSnapshot(2L, 20L, "TRY", "ACTIVE");
        cache.putByIban("TR111111111111111111111111", info1);
        cache.putByIban("TR222222222222222222222222", info2);

        cache.evictByIban("TR111111111111111111111111");

        assertTrue(cache.getByIban("TR111111111111111111111111").isEmpty());
        assertTrue(cache.getByIban("TR222222222222222222222222").isPresent());
    }

    @Test
    void shouldIgnoreNulls() {
        assertDoesNotThrow(() -> {
            cache.putById(null, null);
            cache.putByIban(null, null);
            cache.putIbans(null, null);
            cache.putIbans(Set.of(1L), Map.of());
            cache.evictById(null);
            cache.evictByIban(null);
        });
        assertTrue(cache.getById(null).isEmpty());
        assertTrue(cache.getByIban(null).isEmpty());
        assertTrue(cache.getIbans(null).isEmpty());
    }

    @Test
    void shouldValidateMutationResult() {
        var balance = Money.of("100.00", Currency.TRY);
        var result = new AccountAclPort.MutationResult(1L, 2L, balance, balance);

        assertEquals(1L, result.senderAccountId());
        assertEquals(2L, result.receiverAccountId());
        assertThrows(NullPointerException.class,
                () -> new AccountAclPort.MutationResult(null, 2L, balance, balance));
        assertThrows(NullPointerException.class,
                () -> new AccountAclPort.MutationResult(1L, 2L, null, balance));
    }
}
