package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache.caffeine")
public class CacheProperties {

    private final AccountInfoCache accountInfo = new AccountInfoCache();

    public AccountInfoCache getAccountInfo() {
        return accountInfo;
    }

    public static class AccountInfoCache {
        private long maximumSize = 1000;
        private long expireAfterWrite = 60;

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
