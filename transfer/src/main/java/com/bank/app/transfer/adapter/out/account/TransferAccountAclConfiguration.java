package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * AV: adapter construction lives next to the adapters, not in
 * {@code transfer.config.TransferBeanConfig} (which — like every other BC's
 * config — wires only {@code application.usecase} beans). Keeps the config
 * package free of {@code adapter.out} imports.
 */
@Configuration
public class TransferAccountAclConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TransferAccountAclConfiguration.class);

    /**
     * Single-JVM fallback for dev, test and single-instance use: removes the
     * Redis dependency without changing semantics (invalidation logic is
     * shared via {@code AbstractAccountSnapshotCache}).
     *
     * <p>Never valid with more than one replica: an eviction on one pod never
     * reaches another, so account status/currency reads go stale cluster-wide
     * for up to the TTL. Production pins the shared Redis backend with a
     * literal (not env-overridable) value, guarded by
     * {@code ProductionConfigContractTest} — this fallback must therefore
     * never activate there. The profile check below is defense in depth: if
     * the wiring ever degrades, prod fails fast at startup instead of serving
     * stale snapshots silently.
     */
    @Bean
    @ConditionalOnMissingBean(AccountSnapshotCache.class)
    public AccountSnapshotCache accountInfoCachePort(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "InMemoryAccountInfoCacheAdapter must not run with the prod profile: "
                    + "no shared AccountSnapshotCache backend is registered, so per-JVM "
                    + "caches would go stale across replicas. Fix "
                    + "app.cache.account-info.backend=redis instead.");
        }
        log.warn("No shared AccountSnapshotCache backend registered — falling back to "
                + "single-JVM InMemoryAccountInfoCacheAdapter. Correct for dev/test/single "
                + "instance; must never happen with more than one replica.");
        return new InMemoryAccountInfoCacheAdapter();
    }

    @Bean
    public AccountAclPort accountAclPort(AccountApi accountApi, AccountSnapshotCache cache) {
        return new AccountAclAdapter(accountApi, cache);
    }
}
