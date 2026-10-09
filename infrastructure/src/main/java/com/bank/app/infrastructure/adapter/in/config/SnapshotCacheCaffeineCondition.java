package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the resolved snapshot-cache backend is single-JVM
 * ({@code caffeine}, the default). Complement of
 * {@link SnapshotCacheRedisCondition}: exactly one of the two matches, so the
 * previous {@code matchIfMissing} semantics are preserved.
 */
public class SnapshotCacheCaffeineCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean match = !CacheBackendResolution.isRedis(context.getEnvironment());
        ConditionMessage message = ConditionMessage.forCondition("SnapshotCacheCaffeine")
                .because("snapshot cache backend is " + (match ? "" : "not ") + "single-JVM");
        return new ConditionOutcome(match, message);
    }
}
