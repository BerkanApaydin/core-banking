package com.bank.app.common.application.outbox;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotentOutboxRelay")
@SuppressWarnings("null")
class IdempotentOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneId.of("UTC"));
    private static final String PREFIX = "outbox_handler_";
    private static final EventEntry EVENT = new EventEntry(
            "evt-1", "Transfer", "42", "TransferCompleted", "{}", 0, false, false,
            null, 0, LocalDateTime.of(2026, 10, 22, 11, 0));

    @Mock private IdempotencyPort idempotencyPort;
    @Mock private ClockProviderPort clockProvider;

    private RecordingRelay relay;

    private static final class RecordingRelay extends IdempotentOutboxRelay {
        private final List<EventEntry> delivered = new ArrayList<>();
        private final List<EventEntry> duplicates = new ArrayList<>();
        private RuntimeException deliveryFailure;

        RecordingRelay(IdempotencyPort port, ClockProviderPort clockProvider) {
            super(port, clockProvider, PREFIX);
        }

        RecordingRelay(IdempotencyPort port, ClockProviderPort clockProvider, String prefix) {
            super(port, clockProvider, prefix);
        }

        @Override
        public boolean supports(String eventType) {
            return true;
        }

        @Override
        protected void deliver(EventEntry event) {
            if (deliveryFailure != null) {
                throw deliveryFailure;
            }
            delivered.add(event);
        }

        @Override
        protected void onDuplicateSkipped(EventEntry event) {
            duplicates.add(event);
        }
    }

    private static final class DefaultFailureRelay extends IdempotentOutboxRelay {
        DefaultFailureRelay(IdempotencyPort port) {
            super(port, null, PREFIX);
        }

        @Override
        public boolean supports(String eventType) {
            return true;
        }

        @Override
        protected void deliver(EventEntry event) {
            throw new IllegalStateException("handler exploded");
        }
    }

    @BeforeEach
    void setUp() {
        relay = new RecordingRelay(idempotencyPort, clockProvider);
    }

    private void stubClaim(boolean claimed) {
        when(clockProvider.clock()).thenReturn(FIXED);
        when(idempotencyPort.tryCreateHandler(PREFIX + EVENT.id(), LocalDateTime.now(FIXED)))
                .thenReturn(claimed);
    }

    @Test
    @DisplayName("delivers a first-seen event and leaves a COMPLETED reservation")
    void shouldDeliverAndComplete() {
        stubClaim(true);

        relay.handle(EVENT);

        assertThat(relay.delivered).containsExactly(EVENT);
        verify(idempotencyPort).markCompleted(PREFIX + EVENT.id(), EVENT.eventType(), 200);
        verify(idempotencyPort, never()).markFailed(any());
    }

    @Test
    @DisplayName("skips a COMPLETED duplicate without reopening the claim")
    void shouldSkipCompletedDuplicate() {
        stubClaim(false);
        when(idempotencyPort.findById(PREFIX + EVENT.id()))
                .thenReturn(Optional.of(new IdempotencyPort.Entry(
                        PREFIX + EVENT.id(), "COMPLETED", "{}", 200, LocalDateTime.now(FIXED))));

        relay.handle(EVENT);

        assertThat(relay.delivered).isEmpty();
        assertThat(relay.duplicates).containsExactly(EVENT);
        verify(idempotencyPort, never()).tryResetFailed(any(), any());
    }

    @Test
    @DisplayName("reopens a FAILED reservation and retries delivery")
    void shouldReopenFailedReservation() {
        stubClaim(false);
        when(idempotencyPort.findById(PREFIX + EVENT.id()))
                .thenReturn(Optional.of(new IdempotencyPort.Entry(
                        PREFIX + EVENT.id(), "FAILED", null, null, LocalDateTime.now(FIXED))));
        when(idempotencyPort.tryResetFailed(PREFIX + EVENT.id(), LocalDateTime.now(FIXED)))
                .thenReturn(true);

        relay.handle(EVENT);

        assertThat(relay.delivered).containsExactly(EVENT);
        verify(idempotencyPort).markCompleted(PREFIX + EVENT.id(), EVENT.eventType(), 200);
    }

    @Test
    @DisplayName("skips when another worker wins the FAILED reservation race")
    void shouldSkipWhenReopenLosesRace() {
        stubClaim(false);
        when(idempotencyPort.findById(PREFIX + EVENT.id()))
                .thenReturn(Optional.of(new IdempotencyPort.Entry(
                        PREFIX + EVENT.id(), "FAILED", null, null, LocalDateTime.now(FIXED))));
        when(idempotencyPort.tryResetFailed(PREFIX + EVENT.id(), LocalDateTime.now(FIXED)))
                .thenReturn(false);

        relay.handle(EVENT);

        assertThat(relay.delivered).isEmpty();
        assertThat(relay.duplicates).containsExactly(EVENT);
        verify(idempotencyPort, never()).markCompleted(any(), any(), eq(200));
    }

    @Test
    @DisplayName("skips a claimed key whose state row is gone")
    void shouldSkipWhenClaimHasNoState() {
        stubClaim(false);
        when(idempotencyPort.findById(PREFIX + EVENT.id())).thenReturn(Optional.empty());

        relay.handle(EVENT);

        assertThat(relay.delivered).isEmpty();
        assertThat(relay.duplicates).containsExactly(EVENT);
    }

    @Test
    @DisplayName("marks FAILED and rethrows so the outbox retries")
    void shouldMarkFailedAndWrapFailure() {
        stubClaim(true);
        relay.deliveryFailure = new IllegalStateException("handler exploded");

        assertThatThrownBy(() -> relay.handle(EVENT))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("RecordingRelay failed")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(idempotencyPort).markFailed(PREFIX + EVENT.id());
        verify(idempotencyPort, never()).markCompleted(any(), any(), eq(200));
    }

    @Test
    @DisplayName("keeps the delivery failure primary when markFailed also fails")
    void shouldNotMaskTheDeliveryFailureWhenMarkFailedFails() {
        stubClaim(true);
        relay.deliveryFailure = new IllegalStateException("handler exploded");
        doThrow(new IllegalStateException("bookkeeping down"))
                .when(idempotencyPort).markFailed(PREFIX + EVENT.id());

        assertThatThrownBy(() -> relay.handle(EVENT))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("RecordingRelay failed")
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(thrown -> assertThat(thrown.getCause().getSuppressed()[0])
                        .hasMessage("bookkeeping down"));
    }

    @Test
    @DisplayName("default failure hook names the concrete relay")
    void shouldNameConcreteRelayInDefaultFailureHook() {
        when(idempotencyPort.tryCreateHandler(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> new DefaultFailureRelay(idempotencyPort).handle(EVENT))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("DefaultFailureRelay failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("default duplicate hook skips silently without redelivering")
    void shouldSkipSilentlyWithDefaultDuplicateHook() {
        when(idempotencyPort.tryCreateHandler(any(), any())).thenReturn(false);
        when(idempotencyPort.findById(any())).thenReturn(Optional.of(
                new IdempotencyPort.Entry(PREFIX + EVENT.id(), "COMPLETED", "{}", 200, null)));

        assertThatCode(() -> new DefaultFailureRelay(idempotencyPort).handle(EVENT))
                .doesNotThrowAnyException();

        verify(idempotencyPort, never()).markCompleted(any(), any(), eq(200));
        verify(idempotencyPort, never()).markFailed(any());
    }

    @Test
    @DisplayName("propagates a claim-store failure without delivering")
    void shouldPropagateClaimFailure() {
        when(clockProvider.clock()).thenReturn(FIXED);
        when(idempotencyPort.tryCreateHandler(any(), any()))
                .thenThrow(new IllegalStateException("claim store unavailable"));

        assertThatThrownBy(() -> relay.handle(EVENT))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("claim store unavailable");

        assertThat(relay.delivered).isEmpty();
        verify(idempotencyPort, never()).markCompleted(any(), any(), eq(200));
        verify(idempotencyPort, never()).markFailed(any());
    }

    @Test
    @DisplayName("falls back to the system clock when no provider is wired")
    void shouldFallBackToSystemClock() {
        when(idempotencyPort.tryCreateHandler(any(), any())).thenReturn(true);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        assertThatThrownBy(() -> new DefaultFailureRelay(idempotencyPort).handle(EVENT))
                .isExactlyInstanceOf(RuntimeException.class);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(idempotencyPort).tryCreateHandler(eq(PREFIX + EVENT.id()), captor.capture());
        assertThat(captor.getValue()).isBetween(before, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("rejects a null event before the claim store is touched")
    void shouldRejectNullEvent() {
        when(clockProvider.clock()).thenReturn(FIXED);

        assertThatThrownBy(() -> relay.handle(null))
                .isExactlyInstanceOf(NullPointerException.class);

        // A null event must not consume a dedup key.
        verify(idempotencyPort, never()).tryCreateHandler(any(), any());
        verify(idempotencyPort, never()).markCompleted(any(), any(), eq(200));
        verify(idempotencyPort, never()).markFailed(any());
    }

    @Test
    @DisplayName("fails fast on a missing claim store or dedup prefix")
    void shouldRejectNullConstructorArguments() {
        // Without the guard the relay would only blow up mid-delivery, after
        // the event was already locked and the failure looks like a handler bug.
        assertThatThrownBy(() -> new RecordingRelay(null, clockProvider))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("IdempotencyPort must not be null");
        assertThatThrownBy(() -> new RecordingRelay(idempotencyPort, clockProvider, null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("dedupKeyPrefix must not be null");
    }
}
