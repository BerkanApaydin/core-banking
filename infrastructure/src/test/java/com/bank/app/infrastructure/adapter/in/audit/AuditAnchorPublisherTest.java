package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.audit.application.dto.AuditChainHead;
import com.bank.app.audit.application.port.out.AuditAnchorStorePort;
import com.bank.app.audit.application.port.out.AuditChainHeadPort;
import com.bank.app.audit.application.dto.AuditAnchor;
import com.bank.app.infrastructure.adapter.out.audit.AuditAnchorSigner;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuditAnchorPublisher")
class AuditAnchorPublisherTest {

    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final String OLD_HASH = "c".repeat(64);
    // Derived at runtime (same helper as AuditAnchorSignerTest) so the fixture
    // never lands in the source as a secret-shaped Base64 literal, which the
    // gitleaks generic-api-key rule reports. See docs/decisions/secret-management.md.
    private static final String SECRET = secret("publisher-anchor-key");
    private static final Instant NOW = Instant.parse("2026-10-29T03:45:00Z");
    private static final Instant EARLIER = Instant.parse("2026-10-28T03:45:00Z");

    private final AuditChainHeadPort headPort = mock(AuditChainHeadPort.class);
    private final AuditAnchorStorePort anchorStore = mock(AuditAnchorStorePort.class);
    private final AuditAnchorProperties properties = new AuditAnchorProperties(
            true, "unused", "anchor-v1", SECRET, Map.of());
    private final AuditAnchorSigner signer = new AuditAnchorSigner(properties);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AuditAnchorPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AuditAnchorPublisher(headPort, anchorStore, properties,
                AdvisorySchedulerLock.alwaysRun(),
                () -> Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
    }

    @Test
    @DisplayName("should anchor a new chain head")
    void shouldAnchorNewHead() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.empty());

        publisher.publishAnchor();

        ArgumentCaptor<AuditAnchor> captor = ArgumentCaptor.forClass(AuditAnchor.class);
        verify(anchorStore).append(captor.capture());
        AuditAnchor anchor = captor.getValue();
        assertThat(anchor.lastSeq()).isEqualTo(10);
        assertThat(anchor.lastHash()).isEqualTo(HASH);
        assertThat(anchor.publishedAtEpochSecond()).isEqualTo(NOW.getEpochSecond());
        assertThat(signer.verify(anchor)).isTrue();
        assertThat(counter(AuditAnchorPublisher.PUBLISHED_COUNTER)).isEqualTo(1);
        assertThat(gauge(AuditAnchorPublisher.LAST_SUCCESS_GAUGE))
                .isEqualTo(NOW.getEpochSecond());
        assertThat(gauge(AuditAnchorPublisher.LAST_SEQ_GAUGE)).isEqualTo(10);
    }

    @Test
    @DisplayName("should anchor a fresh zero head once")
    void shouldAnchorGenesis() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(0, "")));
        when(anchorStore.latest()).thenReturn(Optional.empty());

        publisher.publishAnchor();

        ArgumentCaptor<AuditAnchor> captor = ArgumentCaptor.forClass(AuditAnchor.class);
        verify(anchorStore).append(captor.capture());
        assertThat(captor.getValue().lastSeq()).isZero();
        assertThat(captor.getValue().lastHash()).isEmpty();
        assertThat(counter(AuditAnchorPublisher.PUBLISHED_COUNTER)).isEqualTo(1);
    }

    @Test
    @DisplayName("should be a no-op when the head is already anchored")
    void shouldSkipAlreadyAnchoredHead() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(10, HASH, EARLIER)));

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.PUBLISHED_COUNTER)).isZero();
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isZero();
        assertThat(gauge(AuditAnchorPublisher.LAST_SUCCESS_GAUGE))
                .isEqualTo(NOW.getEpochSecond());
    }

    @Test
    @DisplayName("should alarm when the database head is behind the anchor")
    void shouldAlarmWhenDatabaseIsBehind() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(11, HASH, EARLIER)));

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isEqualTo(1);
        assertThat(counter(AuditAnchorPublisher.PUBLISHED_COUNTER)).isZero();
        assertThat(gauge(AuditAnchorPublisher.LAST_SUCCESS_GAUGE)).isZero();
    }

    @Test
    @DisplayName("should alarm when the anchored hash differs at the same sequence")
    void shouldAlarmOnSameSequenceHashMismatch() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(10, OTHER_HASH, EARLIER)));

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isEqualTo(1);
    }

    @Test
    @DisplayName("should alarm when a retained anchored row was rewritten")
    void shouldAlarmOnRetainedPrefixRewrite() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(5, OLD_HASH, EARLIER)));
        when(headPort.findRowHash(5)).thenReturn(Optional.of(OTHER_HASH));

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isEqualTo(1);
    }

    @Test
    @DisplayName("should accept an anchored prefix pruned by retention")
    void shouldAcceptPrunedPrefix() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(5, OLD_HASH, EARLIER)));
        when(headPort.findRowHash(5)).thenReturn(Optional.empty());
        when(headPort.findMinChainSeq()).thenReturn(OptionalLong.of(7));

        publisher.publishAnchor();

        ArgumentCaptor<AuditAnchor> captor = ArgumentCaptor.forClass(AuditAnchor.class);
        verify(anchorStore).append(captor.capture());
        assertThat(captor.getValue().lastSeq()).isEqualTo(10);
        assertThat(counter(AuditAnchorPublisher.PUBLISHED_COUNTER)).isEqualTo(1);
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isZero();
    }

    @Test
    @DisplayName("should alarm when an unpruned anchored row is missing")
    void shouldAlarmOnMissingUnprunedPrefix() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(5, OLD_HASH, EARLIER)));
        when(headPort.findRowHash(5)).thenReturn(Optional.empty());
        when(headPort.findMinChainSeq()).thenReturn(OptionalLong.of(5));

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isEqualTo(1);
    }

    @Test
    @DisplayName("should alarm when the audit table is empty but the head is non-zero")
    void shouldAlarmOnEmptyTableWithNonZeroHead() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(signer.sign(5, OLD_HASH, EARLIER)));
        when(headPort.findRowHash(5)).thenReturn(Optional.empty());
        when(headPort.findMinChainSeq()).thenReturn(OptionalLong.empty());

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isEqualTo(1);
    }

    @Test
    @DisplayName("should alarm on an invalid anchor signature")
    void shouldAlarmOnInvalidSignature() {
        AuditAnchor forged = new AuditAnchor(1, "anchor-v1", 10, HASH,
                EARLIER.getEpochSecond(), "A".repeat(43));
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.of(forged));

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.MISMATCHES_COUNTER)).isEqualTo(1);
    }

    @Test
    @DisplayName("should record a failure when the chain head row is missing")
    void shouldRecordFailureWhenHeadIsMissing() {
        when(headPort.findHead()).thenReturn(Optional.empty());
        when(anchorStore.latest()).thenReturn(Optional.empty());

        publisher.publishAnchor();

        verify(anchorStore, never()).append(any());
        assertThat(counter(AuditAnchorPublisher.FAILURES_COUNTER)).isEqualTo(1);
        assertThat(gauge(AuditAnchorPublisher.LAST_SUCCESS_GAUGE)).isZero();
    }

    @Test
    @DisplayName("should record a failure when the anchor sink cannot append")
    void shouldRecordFailureWhenSinkFails() {
        when(headPort.findHead()).thenReturn(Optional.of(new AuditChainHead(10, HASH)));
        when(anchorStore.latest()).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("disk full")).when(anchorStore).append(any());

        publisher.publishAnchor();

        assertThat(counter(AuditAnchorPublisher.FAILURES_COUNTER)).isEqualTo(1);
        assertThat(counter(AuditAnchorPublisher.PUBLISHED_COUNTER)).isZero();
        assertThat(gauge(AuditAnchorPublisher.LAST_SUCCESS_GAUGE)).isZero();
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private double gauge(String name) {
        return meterRegistry.get(name).gauge().value();
    }

    private static String secret(String value) {
        byte[] key = Arrays.copyOf(value.getBytes(StandardCharsets.UTF_8), 32);
        return Base64.getEncoder().encodeToString(key);
    }
}
