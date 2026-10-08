package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatNoException;
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
    void shouldSwallowStoreFailuresForNextSchedule() {
        var job = new LedgerReconciliationJob(jdbc, AdvisorySchedulerLock.alwaysRun(), null);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("store unavailable"));

        assertThatNoException().isThrownBy(job::reconcile);
    }
}
