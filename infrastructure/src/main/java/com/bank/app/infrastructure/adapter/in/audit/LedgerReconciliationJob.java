package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Long term #7: financial reconciliation job. Promotes the LEDGER_NONZERO_SQL
 * metric from BacklogMetricsReporter to a periodic job: counts
 * transaction_refs that do not net to zero and, when nonzero, logs ERROR and
 * bumps a counter (feeding the Prometheus alert). Read-only, never moves money.
 */
@Component
public class LedgerReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationJob.class);

    static final String LEDGER_NONZERO_SQL = "SELECT count(*) FROM (SELECT transaction_ref FROM ledger_entries "
            + "GROUP BY transaction_ref HAVING SUM(CASE WHEN direction = 'CREDIT' "
            + "THEN amount ELSE -amount END) <> 0) t";
    static final String LOCK_NAME = "ledger-reconciliation";

    private final JdbcTemplate jdbc;
    private final AdvisorySchedulerLock schedulerLock;
    private final MeterRegistry meterRegistry;
    private final AtomicLong nonzeroRefsGauge = new AtomicLong();

    public LedgerReconciliationJob(JdbcTemplate jdbc,
            AdvisorySchedulerLock schedulerLock,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.schedulerLock = schedulerLock;
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            meterRegistry.gauge("ledger.nonzero_transaction_refs", nonzeroRefsGauge);
        }
    }

    @Scheduled(cron = "${app.integrity.ledger-reconciliation-cron:0 30 3 * * *}")
    public void reconcile() {
        schedulerLock.runIfLeader(LOCK_NAME, () -> {
            try {
                Long nonzero = jdbc.queryForObject(LEDGER_NONZERO_SQL, Long.class);
                long count = nonzero != null ? nonzero : 0L;
                nonzeroRefsGauge.set(count);
                if (meterRegistry != null) {
                    meterRegistry.counter("ledger.reconciliation.runs").increment();
                    if (count > 0) {
                        meterRegistry.counter("ledger.reconciliation.nonzero").increment(count);
                    }
                }
                if (count > 0) {
                    log.error("LEDGER RECONCILIATION FAILED: nonzero_transaction_refs={} (investigate immediately)", count);
                } else {
                    log.info("Ledger reconciliation passed: all transaction_refs net to zero");
                }
            } catch (RuntimeException e) {
                // A missed scan only delays detection; the next schedule retries.
                // Never let a store failure kill the scheduler thread.
                log.warn("Ledger reconciliation scan failed: {}", e.getClass().getSimpleName());
            }
        });
    }
}
