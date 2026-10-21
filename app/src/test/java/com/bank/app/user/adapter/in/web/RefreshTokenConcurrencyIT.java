package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R-T10: refresh rotation versus concurrent refresh/logout, against real
 * PostgreSQL. The invariant is that after any interleaving the family has no
 * active refresh token: a logout that races a rotation must also retire the
 * freshly issued successor (the R-T10 bug class), and two concurrent
 * rotations must serialize into exactly one winner whose successor is then
 * killed by the loser's reuse response.
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
class RefreshTokenConcurrencyIT extends AbstractSpringBootIntegrationTest {

    private static final String PASSWORD = "RefreshRace123";
    private static final int ITERATIONS = 6;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final RefreshTokenPort refreshTokenPort;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final LogoutUseCase logoutUseCase;
    private final List<String> createdUsernames = new ArrayList<>();

    @Autowired
    RefreshTokenConcurrencyIT(MockMvc mockMvc, ObjectMapper objectMapper,
            EntityManager entityManager, PlatformTransactionManager transactionManager,
            RefreshTokenPort refreshTokenPort, RefreshSessionUseCase refreshSessionUseCase,
            LogoutUseCase logoutUseCase, ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.refreshTokenPort = refreshTokenPort;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @AfterEach
    void tearDown() {
        if (createdUsernames.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            for (String username : createdUsernames) {
                entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id IN "
                                + "(SELECT id FROM users WHERE username = :username)")
                        .setParameter("username", username)
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM users WHERE username = :username")
                        .setParameter("username", username)
                        .executeUpdate();
            }
        });
        createdUsernames.clear();
    }

    @Test
    void concurrentRefreshesOfTheSameTokenLeaveNoLiveSession() throws Exception {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            String username = registerAndLoginUser();
            JsonNode pair = login(username);
            String refreshToken = pair.get("refreshToken").asText();
            String familyId = familyOf(refreshToken);

            List<Outcome<AuthResponse>> outcomes = runTogether(
                    () -> refreshSessionUseCase.execute(refreshToken),
                    () -> refreshSessionUseCase.execute(refreshToken));
            Outcome<AuthResponse> first = outcomes.get(0);
            Outcome<AuthResponse> second = outcomes.get(1);

            long winners = outcomes.stream().filter(outcome -> outcome.error() == null).count();
            assertThat(winners)
                    .as("iteration %d: exactly one rotation may win, outcomes %s, %s",
                            iteration, first.error(), second.error())
                    .isEqualTo(1);
            assertThat(activeTokens(familyId))
                    .as("iteration %d: the loser's reuse response must revoke the winner's successor",
                            iteration)
                    .isZero();

            AuthResponse winner = outcomes.stream()
                    .filter(outcome -> outcome.error() == null)
                    .findFirst().orElseThrow().result();
            assertThatThrownBy(() -> refreshSessionUseCase.execute(winner.refreshToken()))
                    .as("iteration %d: winner's successor must be dead after the family revoke",
                            iteration)
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void logoutRacingRefreshRevokesTheSuccessor() throws Exception {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            String username = registerAndLoginUser();
            JsonNode pair = login(username);
            String accessToken = pair.get("token").asText();
            String refreshToken = pair.get("refreshToken").asText();
            String familyId = familyOf(refreshToken);

            List<Outcome<Object>> outcomes = runTogether(
                    () -> refreshSessionUseCase.execute(refreshToken),
                    () -> {
                        logoutUseCase.execute("Bearer " + accessToken, refreshToken);
                        return null;
                    });
            Outcome<Object> refresh = outcomes.get(0);
            Outcome<Object> logout = outcomes.get(1);

            assertThat(logout.error())
                    .as("iteration %d: logout must never fail in this race", iteration)
                    .isNull();
            assertThat(activeTokens(familyId))
                    .as("iteration %d: logout must retire any successor committed by the refresh",
                            iteration)
                    .isZero();
            if (refresh.error() == null) {
                AuthResponse rotated = (AuthResponse) refresh.result();
                assertThatThrownBy(() -> refreshSessionUseCase.execute(rotated.refreshToken()))
                        .as("iteration %d: successor issued during logout must not work", iteration)
                        .isInstanceOf(RuntimeException.class);
            }
        }
    }

    private String registerAndLoginUser() {
        String username = "race_" + UUID.randomUUID().toString().replace("-", "");
        createdUsernames.add(username);
        return username;
    }

    private JsonNode login(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest(username, PASSWORD))))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest(username, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String familyOf(String refreshToken) {
        return refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex(refreshToken))
                .orElseThrow().familyId();
    }

    private long activeTokens(String familyId) {
        Number count = transactionTemplate.execute(status -> (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM refresh_tokens "
                        + "WHERE family_id = :familyId AND revoked = false")
                .setParameter("familyId", familyId)
                .getSingleResult());
        return count == null ? 0L : count.longValue();
    }

    private static <T> List<Outcome<T>> runTogether(Callable<T> first, Callable<T> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome<T>> firstFuture = pool.submit(runWhenReleased(first, ready, start));
            Future<Outcome<T>> secondFuture = pool.submit(runWhenReleased(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstFuture.get(30, TimeUnit.SECONDS),
                    secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> Callable<Outcome<T>> runWhenReleased(Callable<T> action,
            CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return new Outcome<>(action.call(), null);
            } catch (Exception failure) {
                return new Outcome<>(null, failure);
            }
        };
    }

    private record Outcome<T>(T result, Exception error) {
    }
}
