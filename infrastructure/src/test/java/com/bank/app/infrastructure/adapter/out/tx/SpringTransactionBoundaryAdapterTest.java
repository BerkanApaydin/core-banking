package com.bank.app.infrastructure.adapter.out.tx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class SpringTransactionBoundaryAdapterTest {

    private final SpringTransactionBoundaryAdapter adapter =
            new SpringTransactionBoundaryAdapter(mock(PlatformTransactionManager.class));

    @Test
    void reportsNoTransactionOutsideOne() {
        assertFalse(adapter.isTransactionActive());
    }

    @Test
    void runsAfterCommitImmediatelyWithoutTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);

        adapter.runAfterCommit(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void runsAfterCompletionImmediatelyWithoutTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);

        adapter.runAfterCompletion(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void rejectsMissingTransactionManager() {
        assertThrows(NullPointerException.class,
                () -> new SpringTransactionBoundaryAdapter(null));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> adapter.executeRequiresNew(() -> "x", 0));
    }

    @Test
    void executesRequiresNewWithManagerUsingRequiresNewAndTimeout(
            @Mock PlatformTransactionManager transactionManager,
            @Mock TransactionStatus status) {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);

        SpringTransactionBoundaryAdapter adapter = new SpringTransactionBoundaryAdapter(transactionManager);

        String result = adapter.executeRequiresNew(() -> "done", 7);

        assertEquals("done", result);
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior());
        assertEquals(7, definition.getValue().getTimeout());
        verify(transactionManager).commit(status);
    }

    @Test
    void defersActionUntilAfterCommitInsideTransaction() {
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

    @Test
    void defersActionUntilAfterCompletionInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);

            AtomicBoolean ran = new AtomicBoolean(false);
            adapter.runAfterCompletion(() -> ran.set(true));
            assertFalse(ran.get(), "hook must not run before completion");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(1));
            assertTrue(ran.get());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
