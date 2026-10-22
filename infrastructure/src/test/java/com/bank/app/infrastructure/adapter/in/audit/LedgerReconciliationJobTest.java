package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.bank.app.infrastructure.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationJobTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void shouldPassWhenEveryRefNetsToZero() {
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), null);
        when(jdbc.queryForObject(eq(LedgerReconciliationJob.LEDGER_NONZERO_SQL), eq(Long.class)))
                .thenReturn(0L);

        assertThatNoException().isThrownBy(job::reconcile);

        verify(jdbc).queryForObject(eq(LedgerReconciliationJob.LEDGER_NONZERO_SQL), eq(Long.class));
    }

    @Test
    void shouldAlertWithoutThrowingWhenRefsDoNotNetToZero() {
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), null);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        // Paging happens through the ledger.nonzero counter + ERROR log;
        // the scheduler itself must never throw and kill future runs.
        assertThatNoException().isThrownBy(job::reconcile);
    }

    @Test
    void shouldPublishNonzeroCountAsGauge() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), registry);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        assertThatNoException().isThrownBy(job::reconcile);

        assertThat(registry.get("ledger.nonzero_transaction_refs").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void shouldSwallowStoreFailuresForNextSchedule() {
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), null);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("store unavailable"));

        assertThatNoException().isThrownBy(job::reconcile);
    }

    @Test
    void shouldCountEveryRunEvenWhenBalanced() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), registry);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        assertThatNoException().isThrownBy(job::reconcile);

        assertThat(registry.counter("ledger.reconciliation.runs").count()).isEqualTo(1.0);
        assertThat(registry.counter("ledger.reconciliation.nonzero").count()).isEqualTo(0.0);
        assertThat(registry.get("ledger.nonzero_transaction_refs").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void shouldCountNonzeroRefsByObservedCount() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), registry);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        assertThatNoException().isThrownBy(job::reconcile);

        assertThat(registry.counter("ledger.reconciliation.runs").count()).isEqualTo(1.0);
        assertThat(registry.counter("ledger.reconciliation.nonzero").count()).isEqualTo(3.0);
    }

    @Test
    void shouldNotCreateNonzeroCounterWhenBalanced() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), registry);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        job.reconcile();

        assertThat(registry.find("ledger.reconciliation.nonzero").counter()).isNull();
    }

    @Test
    void shouldLogPassAndFailureAtTheRightLevel() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        try (LogCapture logs = LogCapture.attach(LedgerReconciliationJob.class)) {
            new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), null).reconcile();
            assertThat(logs.messages(ch.qos.logback.classic.Level.INFO))
                    .anyMatch(message -> message.contains("passed"));
            assertThat(logs.messages(ch.qos.logback.classic.Level.ERROR)).isEmpty();
        }

        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        try (LogCapture logs = LogCapture.attach(LedgerReconciliationJob.class)) {
            new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), null).reconcile();
            assertThat(logs.messages(ch.qos.logback.classic.Level.ERROR))
                    .anyMatch(message -> message.contains("FAILED"));
            assertThat(logs.messages(ch.qos.logback.classic.Level.INFO)).isEmpty();
        }
    }

    @Test
    void shouldTreatNullCountAsZero() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), registry);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        assertThatNoException().isThrownBy(job::reconcile);

        assertThat(registry.get("ledger.nonzero_transaction_refs").gauge().value()).isEqualTo(0.0);
        assertThat(registry.counter("ledger.reconciliation.runs").count()).isEqualTo(1.0);
        assertThat(registry.counter("ledger.reconciliation.nonzero").count()).isEqualTo(0.0);
    }
}
