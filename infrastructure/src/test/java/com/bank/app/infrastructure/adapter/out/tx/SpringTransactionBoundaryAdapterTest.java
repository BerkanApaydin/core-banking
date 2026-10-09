package com.bank.app.infrastructure.adapter.out.tx;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTransactionBoundaryAdapterTest {

    private final SpringTransactionBoundaryAdapter withoutTxManager =
            new SpringTransactionBoundaryAdapter(null);

    @Test
    void reportsNoTransactionOutsideOne() {
        assertFalse(withoutTxManager.isTransactionActive());
    }

    @Test
    void runsAfterCommitImmediatelyWithoutTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);

        withoutTxManager.runAfterCommit(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void executesDirectlyWithoutTransactionManager() {
        assertEquals("done", withoutTxManager.executeRequiresNew(() -> "done", 30));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        SpringTransactionBoundaryAdapter adapter = new SpringTransactionBoundaryAdapter(null);

        assertThrows(IllegalArgumentException.class, () -> adapter.executeRequiresNew(() -> "x", 0));
    }

    @Test
    void defersActionUntilAfterCommitInsideTransaction() {
        SpringTransactionBoundaryAdapter adapter = new SpringTransactionBoundaryAdapter(null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertTrue(adapter.isTransactionActive());

            AtomicBoolean ran = new AtomicBoolean(false);
            adapter.runAfterCommit(() -> ran.set(true));
            assertFalse(ran.get(), "hook must not run before commit");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCommit());
            assertTrue(ran.get());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
