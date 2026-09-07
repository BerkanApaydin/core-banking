package com.bank.app.infrastructure.adapter.out.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerAuditFailureAdapterTest {

    private SimpleMeterRegistry meterRegistry;
    private MicrometerAuditFailureAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adapter = new MicrometerAuditFailureAdapter(meterRegistry);
    }

    @Test
    void shouldIncrementCounterTaggedByActionAndReason() {
        adapter.recordFailure("TRANSFER_CANCELLED", "DataAccessException");
        adapter.recordFailure("TRANSFER_CANCELLED", "DataAccessException");

        assertEquals(2.0, meterRegistry.counter("audit.persist.failures",
                "action", "TRANSFER_CANCELLED", "reason", "DataAccessException").count());
    }

    @Test
    void shouldDefaultNullTagsToUnknown() {
        adapter.recordFailure(null, null);

        assertEquals(1.0, meterRegistry.counter("audit.persist.failures",
                "action", "unknown", "reason", "unknown").count());
    }

    @Test
    void shouldIgnoreFailuresWhenNoRegistryAvailable() {
        MicrometerAuditFailureAdapter noMetrics = new MicrometerAuditFailureAdapter(null);

        noMetrics.recordFailure("TRANSFER_CANCELLED", "DataAccessException");
    }
}
