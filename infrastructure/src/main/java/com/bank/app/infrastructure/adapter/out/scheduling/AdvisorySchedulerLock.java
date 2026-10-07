package com.bank.app.infrastructure.adapter.out.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Single-flight guard for {@code @Scheduled} maintenance jobs (K12/D5).
 *
 * <p>Every replica runs its own schedulers (HPA 2–6 pods). Without
 * coordination, the orphan scan, idempotency cleanup, backlog scan and
 * revocation cleanup each run N times — N× DB load and N× alarms. This guard
 * runs the work only on the replica that holds a PostgreSQL
 * <em>transaction-scoped</em> advisory lock ({@code pg_try_advisory_xact_lock});
 * losers skip silently.
 *
 * <p>Why transaction-scoped (not session-scoped): session locks leak across
 * pooled connections (unlock on another connection silently fails), which
 * would wedge the job forever. Transaction locks release automatically at
 * commit/rollback — no leak path by construction. The lock and the work share
 * one transaction owned by the internal {@link TransactionTemplate}, so call
 * sites must NOT add their own {@code @Transactional} (a contended lock marks
 * rollback-only, which would poison an outer transaction).
 *
 * <p>Failure policy: explicit contention (false) → skip (fail closed, no
 * duplicate work). Lock-query <em>error</em> → run anyway (fail open): cleanup
 * and scan work is idempotent, while skipping forever on a transient error
 * would wedge retention and staleness gauges.
 */
@Component
public class AdvisorySchedulerLock {

    private static final Logger log = LoggerFactory.getLogger(AdvisorySchedulerLock.class);

    static final String LOCK_SQL = "SELECT pg_try_advisory_xact_lock(hashtext(?))";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Autowired
    public AdvisorySchedulerLock(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setTimeout(30);
        this.tx = template;
    }

    // Test/support construction with an explicit template.
    public AdvisorySchedulerLock(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private AdvisorySchedulerLock() {
        this.jdbc = null;
        this.tx = null;
    }

    /**
     * Always-run variant for isolated unit tests (no DB): skips locking and
     * executes the work inline. Production wiring always uses the
     * database-backed constructors above.
     */
    public static AdvisorySchedulerLock alwaysRun() {
        return new AdvisorySchedulerLock() {
            @Override
            public boolean runIfLeader(String lockName, Runnable work) {
                work.run();
                return true;
            }

            @Override
            protected boolean tryAcquire(String lockName) {
                // Null jdbc/tx (private no-arg ctor) must never be touched:
                // this test double never reaches the database-backed path.
                return true;
            }
        };
    }

    /**
     * Runs {@code work} iff this replica acquires {@code lockName}.
     *
     * @return true if the work ran, false if another replica holds the lock.
     */
    public boolean runIfLeader(String lockName, Runnable work) {
        Boolean ran = tx.execute(status -> {
            if (!tryAcquire(lockName)) {
                log.debug("Scheduler lock '{}' held by another replica; skipping.", lockName);
                status.setRollbackOnly();
                return false;
            }
            work.run();
            return true;
        });
        return Boolean.TRUE.equals(ran);
    }

    protected boolean tryAcquire(String lockName) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(LOCK_SQL, Boolean.class, lockName));
        } catch (RuntimeException e) {
            log.warn("Scheduler lock query failed; running unguarded: failureType={}",
                    e.getClass().getSimpleName());
            return true;
        }
    }
}
