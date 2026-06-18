package com.bank.app.transfer.application.usecase;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.SpringDataAccountRepo;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.infrastructure.persistence.SpringDataTransferRepo;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.bank.app.common.security.SecurityUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTransferIntegrationTest {

    @Autowired
    private PlaceTransferUseCase placeTransferUseCase;

    @Autowired
    private SpringDataAccountRepo accountRepo;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpringDataTransferRepo transferRepo;

    @MockitoBean
    private SecurityUtils securityUtils;

    private UserJpaEntity user;

    @BeforeEach
    void setUp() {
        transferRepo.deleteAll();
        accountRepo.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(new UserJpaEntity(null, "user1", "password", "ROLE_USER"));

        accountRepo.save(new AccountJpaEntity(null, user.getId(), "TR290006200000000000000111", "Sender",
                new BigDecimal("1000.00"), "TRY", true));
        accountRepo.save(new AccountJpaEntity(null, user.getId(), "TR290006200000000000000222", "Receiver",
                new BigDecimal("1000.00"), "TRY", true));

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(user.getId()));
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));
    }

    @Test
    void shouldExecuteConcurrentTransfersSuccessfully() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("10.00"),
                Money.Currency.TRY
        );

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    latch.await(); // Wait for all threads to be ready
                    placeTransferUseCase.execute(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown(); // Trigger all threads
        finishLatch.await(30, TimeUnit.SECONDS); // Wait for execution
        executorService.shutdown();

        assertEquals(threadCount, successCount.get(), "All parallel transfers should complete successfully");
        assertEquals(0, failureCount.get(), "There should be no concurrent locking failures");

        BigDecimal balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
        BigDecimal balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();

        assertEquals(new BigDecimal("900.00"), balanceSender, "Sender balance should be exactly 900.00");
        assertEquals(new BigDecimal("1100.00"), balanceReceiver, "Receiver balance should be exactly 1100.00");
    }
}
