package com.bank.app.transfer.application.usecase;

import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.bank.app.infrastructure.adapter.out.security.SecurityContextAdapter;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.context.SpringBootTest;

import com.bank.app.common.application.port.out.EventPublisherPort;

@SuppressWarnings("null")
@SpringBootTest(classes = com.bank.app.BankApplication.class)
class ConcurrencyTransferIntegrationTest extends AbstractSpringBootIntegrationTest {

    @MockitoBean
    private EventPublisherPort eventPublisherPort;

    @Autowired
    private AccountAclPort accountAclPort;

    @Autowired
    private AccountJpaRepository accountRepo;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private OutboxJpaRepository outboxEventRepo;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private SecurityContextAdapter securityUtils;

    private UserJpaEntity user;
    private Long senderAccountId;
    private Long receiverAccountId;

    void runInNewTx(Runnable action) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.execute(status -> {
            action.run();
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        runInNewTx(() -> {
            user = userRepository.save(new UserJpaEntity(null, "user1", "password", "ROLE_USER", null, null, null));

            AccountJpaEntity sender = accountRepo.save(new AccountJpaEntity(null, user.getId(),
                    "TR290006200000000000000111", "Sender", new BigDecimal("1000.00"), "TRY", "ACTIVE", null));
            AccountJpaEntity receiver = accountRepo.save(new AccountJpaEntity(null, user.getId(),
                    "TR290006200000000000000222", "Receiver", new BigDecimal("1000.00"), "TRY", "ACTIVE", null));
            senderAccountId = sender.getId();
            receiverAccountId = receiver.getId();
        });

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(user.getId()));
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));
    }

    @AfterEach
    void tearDown() {
        runInNewTx(() -> {
            outboxEventRepo.deleteAll();
            entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        });
    }

    @Test
    void shouldExecuteConcurrentTransfersSuccessfully() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Long senderId = senderAccountId;
        Long receiverId = receiverAccountId;

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    latch.await();
                    var tt = new TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        accountAclPort.debitAndCredit(
                                senderId, receiverId, new Money(new BigDecimal("10.00"), Currency.TRY));
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown();
        finishLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertEquals(threadCount, successCount.get(), "All parallel transfers should complete successfully");
        assertEquals(0, failureCount.get(), "There should be no concurrent locking failures");

        BigDecimal balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
        BigDecimal balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();

        assertEquals(new BigDecimal("900.00"), balanceSender, "Sender balance should be exactly 900.00");
        assertEquals(new BigDecimal("1100.00"), balanceReceiver, "Receiver balance should be exactly 1100.00");
    }

    @Test
    void shouldHandleOverdraftProtectionUnderConcurrentLoad() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Long senderId = senderAccountId;
        Long receiverId = receiverAccountId;

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    latch.await();
                    var tt = new TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        accountAclPort.debitAndCredit(
                                senderId, receiverId, new Money(new BigDecimal("250.00"), Currency.TRY));
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown();
        finishLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertEquals(4, successCount.get(), "Exactly 4 transfers should succeed");
        assertEquals(1, failureCount.get(), "Exactly 1 transfer should fail due to insufficient balance");

        BigDecimal balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
        BigDecimal balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();

        assertEquals(new BigDecimal("0.00"), balanceSender, "Sender balance should be 0.00");
        assertEquals(new BigDecimal("2000.00"), balanceReceiver, "Receiver balance should be 2000.00");
    }
}
