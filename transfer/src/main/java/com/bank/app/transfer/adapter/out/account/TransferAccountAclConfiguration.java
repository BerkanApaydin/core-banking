package com.bank.app.transfer.adapter.out.account;

import com.bank.app.account.api.AccountApi;
import com.bank.app.account.api.AccountSnapshotCache;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AV: adapter construction lives next to the adapters, not in
 * {@code transfer.config.TransferBeanConfig} (which — like every other BC's
 * config — wires only {@code application.usecase} beans). Keeps the config
 * package free of {@code adapter.out} imports.
 *
 * <p><b>There is deliberately no fallback bean here.</b> An earlier version
 * provided a single-JVM {@code InMemoryAccountSnapshotCacheAdapter} under
 * {@code @ConditionalOnMissingBean(AccountSnapshotCache.class)}, so a
 * deployment that wired no shared backend would silently serve per-pod
 * snapshots — stale account status and currency across the cluster, with
 * nothing in the logs to say so. The runtime backend is now always supplied
 * by the {@code infrastructure} module (Caffeine for dev/test, Redis for
 * production), and the per-JVM implementation is a test double that is not
 * on the production classpath at all. The invariant is structural, not
 * conditional.
 */
@Configuration
public class TransferAccountAclConfiguration {

    @Bean
    public AccountAclPort accountAclPort(AccountApi accountApi, AccountSnapshotCache cache) {
        return new AccountAclAdapter(accountApi, cache);
    }
}
