package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the resolved snapshot-cache backend is {@code caffeine}
 * (single-JVM, the default). Exact match on purpose: an unknown value
 * (a typo such as {@code rediss}) must select <em>no</em> backend, so the
 * startup validator rejects it instead of silently degrading to a
 * per-JVM cache that no replica can invalidate.
 */
public class SnapshotCacheCaffeineCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String backend = CacheBackendResolution.resolve(context.getEnvironment()).backend();
        boolean match = "caffeine".equalsIgnoreCase(backend);
        ConditionMessage message = ConditionMessage.forCondition("SnapshotCacheCaffeine")
                .because("snapshot cache backend is " + (match ? "" : "not ") + "single-JVM");
        return new ConditionOutcome(match, message);
    }
}
