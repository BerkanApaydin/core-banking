package com.bank.app.infrastructure.adapter.out.metrics;

import com.bank.app.audit.application.port.out.AuditFailurePort;
import com.bank.app.audit.application.port.out.AuditObservationPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Exposes swallowed audit persistence failures as a Micrometer counter
 * ({@code audit.persist.failures}, tagged by action) for alerting, plus the
 * pipeline-liveness counter ({@code audit.event.consumed}, tagged by action).
 * The registry is optional so slices without Actuator still start; in that case
 * failures remain visible only in logs (as before this adapter existed).
 */
@Component
public class MicrometerAuditFailureAdapter implements AuditFailurePort, AuditObservationPort {

    private final MeterRegistry meterRegistry;

    public MicrometerAuditFailureAdapter(@Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordFailure(String action, String reason) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("audit.persist.failures",
                "action", action == null ? "unknown" : action,
                "reason", reason == null ? "unknown" : reason)
                .increment();
    }

    @Override
    public void recordConsumed(String action) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("audit.event.consumed",
                "action", action == null ? "unknown" : action)
                .increment();
    }
}
