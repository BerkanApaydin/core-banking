package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache.caffeine")
public class CacheProperties {

    private final AccountInfoCache accountInfo = new AccountInfoCache();

    public AccountInfoCache getAccountInfo() {
        return accountInfo;
    }

    public static class AccountInfoCache {
        /**
         * Snapshot-cache backend selector.
         *
         * <p>Historical prefix ({@code app.cache.caffeine...}) is kept for
         * backward compatibility; the value selects the backend:
         * {@code caffeine} = single-JVM (dev/test/single instance),
         * {@code redis} = shared across replicas (production).
         */
        private String backend = "caffeine";
        private long maximumSize = 1000;
        private long expireAfterWrite = 60;

        public String getBackend() {
            return backend;
        }
        public void setBackend(String backend) {
            this.backend = backend;
        }
        public long getMaximumSize() {
            return maximumSize;
        }
        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
        public long getExpireAfterWrite() {
            return expireAfterWrite;
        }
        public void setExpireAfterWrite(long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }
    }
}
