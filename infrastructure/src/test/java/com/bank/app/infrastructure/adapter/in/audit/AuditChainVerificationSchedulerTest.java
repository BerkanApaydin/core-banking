package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.audit.application.port.out.AuditChainVerificationPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditChainVerificationScheduler")
class AuditChainVerificationSchedulerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-28T03:30:00Z"), ZoneOffset.UTC);

    @Mock
    private AuditChainVerificationPort verificationPort;

    private SimpleMeterRegistry registry;
    private AuditChainVerificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        scheduler = new AuditChainVerificationScheduler(verificationPort, () -> FIXED_CLOCK, registry);
    }

    @Test
    @DisplayName("should publish a healthy chain and the verification timestamp")
    void shouldPublishHealthyChain() {
        when(verificationPort.findFirstBrokenChainSeq()).thenReturn(OptionalLong.empty());

        scheduler.verifyChain();

        assertThat(gauge("audit.chain.broken")).isZero();
        assertThat(gauge("audit.chain.last_verified_epoch_seconds"))
                .isEqualTo(FIXED_CLOCK.instant().getEpochSecond());
    }

    @Test
    @DisplayName("should flip the broken gauge when a link is broken")
    void shouldPublishBrokenChain() {
        when(verificationPort.findFirstBrokenChainSeq()).thenReturn(OptionalLong.of(42L));

        scheduler.verifyChain();

        assertThat(gauge("audit.chain.broken")).isEqualTo(1.0);
        assertThat(gauge("audit.chain.last_verified_epoch_seconds"))
                .isEqualTo(FIXED_CLOCK.instant().getEpochSecond());
    }

    @Test
    @DisplayName("should not claim a broken chain when verification itself fails")
    void shouldKeepGaugesWhenVerificationFails() {
        when(verificationPort.findFirstBrokenChainSeq())
                .thenThrow(new IllegalStateException("database unavailable"));

        scheduler.verifyChain();

        assertThat(gauge("audit.chain.broken")).isZero();
        assertThat(gauge("audit.chain.last_verified_epoch_seconds")).isZero();
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
