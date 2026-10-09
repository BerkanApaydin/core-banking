package com.bank.app.transfer.adapter.in.scheduler;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaEntity;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaRepository;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.transfer.config.TransferReaperProperties;
import com.bank.app.transfer.domain.TransferStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * HIGH-1 regression: {@code TransferPendingReaper} must run its
 * {@code PESSIMISTIC_WRITE} lock inside a real (read-write) transaction.
 *
 * <p>Before the fix the scheduler called {@code findByIdForUpdate} with no
 * transaction, so Spring Data opened a read-only one and PostgreSQL rejected
 * {@code SELECT ... FOR UPDATE} with {@code 25006} — the reaper logged a
 * warning per row and never reaped anything. This test boots the full app
 * against Testcontainers PostgreSQL, seeds one stale PENDING row and one
 * fresh PENDING row, runs {@code reap()}, and asserts the stale row is FAILED
 * while the fresh one stays PENDING.
 */
@SpringBootTest(classes = BankApplication.class)
class TransferPendingReaperIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final TransferPendingReaper reaper;
    private final TransferJpaRepository transferRepo;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final LoadTransferPort loadTransferPort;
    private final SaveTransferPort saveTransferPort;
    private final AuditEventPort auditEventPort;
    private final TransferReaperProperties reaperProperties;
    private final ClockProviderPort clockProvider;

    @Autowired
    TransferPendingReaperIntegrationTest(TransferPendingReaper reaper,
            TransferJpaRepository transferRepo,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            LoadTransferPort loadTransferPort,
            SaveTransferPort saveTransferPort,
            AuditEventPort auditEventPort,
            TransferReaperProperties reaperProperties,
            ClockProviderPort clockProvider,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.reaper = reaper;
        this.transferRepo = transferRepo;
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.auditEventPort = auditEventPort;
        this.reaperProperties = reaperProperties;
        this.clockProvider = clockProvider;
    }

    private void runInNewTx(Runnable action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.execute(status -> {
            action.run();
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        runInNewTx(() -> {
            entityManager.createNativeQuery("DELETE FROM ledger_entries").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO users (id, username, password, role, created_at) VALUES "
                    + "(900, 'reaper_user', 'pass', 'ROLE_USER', NOW())")
                    .executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, status, version, created_at) VALUES "
                    + "(901, 900, 'TR770006200000000000000901', 'Sender', 1000.00, 'TRY', 'ACTIVE', 0, NOW()), "
                    + "(902, 900, 'TR870006200000000000000902', 'Receiver', 1000.00, 'TRY', 'ACTIVE', 0, NOW())")
                    .executeUpdate();
            // Stale PENDING (business time 1h ago, older than the 15m threshold).
            entityManager.createNativeQuery(
                    "INSERT INTO transfers (id, sender_account_id, receiver_account_id, amount, currency, status, "
                    + "business_created_at, version, created_at) VALUES "
                    + "(991, 901, 902, 10.00, 'TRY', 'PENDING', NOW() - INTERVAL '1 hour', 0, NOW() - INTERVAL '1 hour'), "
                    + "(992, 901, 902, 20.00, 'TRY', 'PENDING', NOW(), 0, NOW())")
                    .executeUpdate();
        });
    }

    @AfterEach
    void tearDown() {
        runInNewTx(() -> {
            entityManager.createNativeQuery("DELETE FROM ledger_entries").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM audit_logs").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        });
    }

    @Test
    void shouldReapStalePendingInRealTransaction() {
        assertThatCode(reaper::reap).doesNotThrowAnyException();

        TransferJpaEntity stale = transferRepo.findById(991L).orElseThrow();
        assertThat(stale.getStatus()).isEqualTo(TransferStatus.FAILED);

        TransferJpaEntity fresh = transferRepo.findById(992L).orElseThrow();
        assertThat(fresh.getStatus()).isEqualTo(TransferStatus.PENDING);
    }
}
