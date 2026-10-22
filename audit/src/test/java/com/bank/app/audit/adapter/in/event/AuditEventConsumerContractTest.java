package com.bank.app.audit.adapter.in.event;

import com.bank.app.common.domain.event.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Declarative contract of the audit observer. The annotations on the
 * production method are the contract itself: they decide WHEN the observation
 * runs and whether it may open a transaction, and both are load-bearing for
 * the money movement.
 */
class AuditEventConsumerContractTest {

    private static Method listenerMethod() throws NoSuchMethodException {
        return AuditEventConsumer.class.getMethod("onAuditEvent", AuditEvent.class);
    }

    @Test
    void shouldObserveTheEventOnlyAfterTheTransactionCommitted() throws Exception {
        TransactionalEventListener annotation =
                listenerMethod().getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        // BEFORE_COMMIT would observe a row that the commit may still roll back.
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void shouldStillObserveWhenThereIsNoTransactionToFallBackOn() throws Exception {
        TransactionalEventListener annotation =
                listenerMethod().getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        // Without fallback the event is silently dropped whenever it is
        // published outside a transaction (a non-transactional use case).
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    void shouldNotOpenATransactionOfItsOwn() throws Exception {
        // Re-adding @Transactional here borrows a second pooled connection
        // while the committing thread still holds its own — under load that
        // pool-starves the workers into a deadlock.
        assertThat(listenerMethod().getAnnotation(Transactional.class)).isNull();
        assertThat(AuditEventConsumer.class.getAnnotation(Transactional.class)).isNull();
    }
}
