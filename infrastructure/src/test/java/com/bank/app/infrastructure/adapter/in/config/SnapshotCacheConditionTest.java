package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.infrastructure.adapter.out.cache.CaffeineAccountInfoCacheAdapter;
import com.bank.app.infrastructure.adapter.out.cache.RedisAccountSnapshotCacheAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SnapshotCacheConditionTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withUserConfiguration(AccountCacheAliasConfig.class, CacheConfig.class,
                        CaffeineAccountInfoCacheAdapter.class, RedisAccountSnapshotCacheAdapter.class);
    }

    @Test
    void defaultsToSingleJvmBackend() {
        runner().run(context -> {
            assertThat(context).hasSingleBean(CaffeineAccountInfoCacheAdapter.class);
            assertThat(context).doesNotHaveBean(RedisAccountSnapshotCacheAdapter.class);
        });
    }

    @Test
    void canonicalKeySelectsRedisBackend() {
        runner().withPropertyValues("app.cache.account-info.backend=redis").run(context -> {
            assertThat(context).hasSingleBean(RedisAccountSnapshotCacheAdapter.class);
            assertThat(context).doesNotHaveBean(CaffeineAccountInfoCacheAdapter.class);
            assertThat(context.getBean(AccountSnapshotCache.class))
                    .isInstanceOf(RedisAccountSnapshotCacheAdapter.class);
        });
    }

    @Test
    void legacyKeyStillSelectsRedisBackend() {
        runner().withPropertyValues("app.cache.caffeine.account-info.backend=redis").run(context -> {
            assertThat(context).hasSingleBean(RedisAccountSnapshotCacheAdapter.class);
            assertThat(context).doesNotHaveBean(CaffeineAccountInfoCacheAdapter.class);
        });
    }

    @Test
    void resolvedSettingsAgreeWithSelectedBackend() {
        runner().withPropertyValues("app.cache.account-info.backend=redis").run(context -> {
            CacheProperties.AccountInfoCache settings =
                    context.getBean("resolvedAccountInfoCache", CacheProperties.AccountInfoCache.class);
            assertThat(settings.backend()).isEqualTo("redis");
        });
    }
}
