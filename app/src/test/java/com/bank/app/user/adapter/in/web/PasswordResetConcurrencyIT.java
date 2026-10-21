package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R-U12: concurrent password-reset requests must leave exactly one live
 * (unused) token. The request use case serializes delete+insert with a
 * pessimistic lock on the user row; V58 adds the partial unique index as the
 * database-level invariant.
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
class PasswordResetConcurrencyIT extends AbstractSpringBootIntegrationTest {

    private static final String USERNAME = "reset_race";
    private static final String PASSWORD = "Race_passw0rd12";
    private static final int CONCURRENCY = 8;

    private final MockMvc mockMvc;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    PasswordResetConcurrencyIT(MockMvc mockMvc, EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM password_reset_tokens WHERE user_id IN "
                            + "(SELECT id FROM users WHERE username = :username)")
                    .setParameter("username", USERNAME)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE username = :username")
                    .setParameter("username", USERNAME)
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName("concurrent reset requests leave exactly one live token")
    void concurrentRequestsLeaveOneLiveToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + USERNAME + "\", \"password\": \""
                                + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                responses.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/auth/password/reset/request")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"username\": \"" + USERNAME + "\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> response : responses) {
                assertThat(response.get(15, TimeUnit.SECONDS)).isEqualTo(202);
            }
        } finally {
            pool.shutdownNow();
        }

        Number liveTokens = (Number) transactionTemplate.execute(status ->
                entityManager.createNativeQuery("SELECT count(*) FROM password_reset_tokens t "
                                + "JOIN users u ON u.id = t.user_id "
                                + "WHERE u.username = :username AND t.used_at IS NULL")
                        .setParameter("username", USERNAME)
                        .getSingleResult());

        assertThat(liveTokens.longValue()).isEqualTo(1);
    }
}
