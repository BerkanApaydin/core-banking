package com.bank.app.transfer.application.port.out;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountInfoCachePortDefaultsTest {

    static class CountingCache implements AccountInfoCachePort {
        int evictAllCalls;

        @Override
        public Optional<AccountAclPort.AccountInfo> getById(Long accountId) {
            return Optional.empty();
        }

        @Override
        public void putById(Long accountId, AccountAclPort.AccountInfo info) {
        }

        @Override
        public Optional<AccountAclPort.AccountInfo> getByIban(String ibanValue) {
            return Optional.empty();
        }

        @Override
        public void putByIban(String ibanValue, AccountAclPort.AccountInfo info) {
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
    void shouldDelegateGranularEvictionsToEvictAllByDefault() {
        CountingCache cache = new CountingCache();

        cache.evictById(1L);
        cache.evictByIban("TR111");
        cache.evictIbansBatch();

        assertEquals(3, cache.evictAllCalls);
    }
}
