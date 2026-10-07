package com.bank.app.accountapi;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void shouldDelegateGranularEvictionToEvictAllByDefault() {
        CountingCache cache = new CountingCache();

        cache.evictById(1L);

        assertEquals(1, cache.evictAllCalls);
    }
}
