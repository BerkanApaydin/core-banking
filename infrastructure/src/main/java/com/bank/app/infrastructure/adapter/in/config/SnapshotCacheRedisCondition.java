package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the resolved snapshot-cache backend is {@code redis}
 * (canonical-wins resolution, see {@link CacheBackendResolution}).
 * Replaces the legacy single-key {@code @ConditionalOnProperty} so the
 * canonical {@code app.cache.account-info.backend} key selects the backend too.
 */
public class SnapshotCacheRedisCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean match = CacheBackendResolution.isRedis(context.getEnvironment());
        ConditionMessage message = ConditionMessage.forCondition("SnapshotCacheRedis")
                .because("snapshot cache backend is " + (match ? "" : "not ") + "redis");
        return new ConditionOutcome(match, message);
    }
}
