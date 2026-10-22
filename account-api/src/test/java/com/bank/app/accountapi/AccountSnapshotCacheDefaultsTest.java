package com.bank.app.accountapi;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountSnapshotCacheDefaultsTest {

    static class CountingCache implements AccountSnapshotCache {
        int evictAllCalls;

        @Override
        public Optional<AccountSnapshot> getById(Long accountId) {
            return Optional.empty();
        }

        @Override
        public void putById(Long accountId, AccountSnapshot snapshot) {
        }

        @Override
        public Optional<AccountSnapshot> getByIban(String ibanValue) {
            return Optional.empty();
        }

        @Override
        public void putByIban(String ibanValue, AccountSnapshot snapshot) {
        }

        @Override
        public Optional<Map<Long, String>> getIbans(Collection<Long> accountIds) {
            return Optional.empty();
        }

        @Override
        public void putIbans(Collection<Long> accountIds, Map<Long, String> ibans) {
        }

        @Override
        public void evictAll() {
            evictAllCalls++;
        }
    }

    @Test
    void shouldRejectGranularEvictionInsteadOfWipingTheWholeRegion() {
        // A backend that forgets evictById must fail loudly. The previous
        // default delegated to evictAll(), which silently wiped every cached
        // account — a database stampede whose blast radius grows with the
        // account count.
        CountingCache cache = new CountingCache();

        assertThrows(UnsupportedOperationException.class, () -> cache.evictById(1L));

        assertEquals(0, cache.evictAllCalls);
    }

    @Test
    void shouldRejectANullIbanInsteadOfBuildingAKeyFromIt() {
        assertThrows(NullPointerException.class, () -> AccountSnapshotCache.ibanKey(null));
    }

    @Test
    void shouldNormalizeEveryBlankIbanOntoTheSameSharedKey() {
        // All whitespace variants collapse to the empty key, so a caller with a
        // garbage IBAN addresses the same cache entry as every other one.
        assertEquals("", AccountSnapshotCache.ibanKey(""));
        assertEquals(AccountSnapshotCache.ibanKey(""), AccountSnapshotCache.ibanKey("   "));
        assertEquals(AccountSnapshotCache.ibanKey(""), AccountSnapshotCache.ibanKey("\t\n"));
    }

    @Test
    void shouldStripAllWhitespaceAndUpperCaseTheIbanKey() {
        assertEquals("TR1234", AccountSnapshotCache.ibanKey("tr 12 34"));
        assertEquals("TR1234", AccountSnapshotCache.ibanKey("\ttr12\n34 "));
        assertEquals("TR1234", AccountSnapshotCache.ibanKey("TR 12 34"));
    }
}
