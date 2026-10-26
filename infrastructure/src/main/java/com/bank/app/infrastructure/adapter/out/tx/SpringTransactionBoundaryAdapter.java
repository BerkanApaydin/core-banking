package com.bank.app.infrastructure.adapter.out.tx;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Spring-based {@link TransactionBoundaryPort} — the only production
 * implementation. Single home for every programmatic transaction read in the
 * system so bounded contexts never touch Spring transaction support classes
 * (guarded by {@code CodingRulesArchitectureTest}).
 *
 * <p>The transaction manager is required: running a REQUIRES_NEW action
 * without a transaction would silently violate the boundary contract, so
 * construction fails fast instead of falling back to a non-transactional
 * execution path.
 */
@Component
public class SpringTransactionBoundaryAdapter implements TransactionBoundaryPort {

    private final PlatformTransactionManager transactionManager;

    @Autowired
    public SpringTransactionBoundaryAdapter(PlatformTransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(
                transactionManager, "PlatformTransactionManager must not be null");
    }

    @Override
    public boolean isTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Override
    public void runAfterCommit(Runnable action) {
        if (!isTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Override
    public <T> T executeRequiresNew(Supplier<T> action, int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive: " + timeoutSeconds);
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(timeoutSeconds);
        return template.execute(status -> action.get());
    }
}
