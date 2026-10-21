package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.infrastructure.adapter.out.security.CaffeineLoginAttemptAdapter;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.domain.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R-U01 regression: login is deliberately NOT transactional, but the TOTP
 * replay counter is written through a JPA {@code @Modifying} statement that
 * requires a transaction. Unlike {@link MfaFlowIntegrationTest}, this class
 * has NO class-level {@code @Transactional}; a test-managed transaction would
 * mask the missing REQUIRES_NEW boundary (the production bug). The second
 * login with the same code proves the counter actually committed: without the
 * fix the request fails with a transaction error, and without a persisted
 * counter the replay would be accepted.
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
@DisplayName("MFA replay counter outside a caller transaction")
class MfaTransactionBoundaryIntegrationTest extends AbstractSpringBootIntegrationTest {

    private static final String USERNAME = "mfa_tx_boundary";
    private static final String PASSWORD = "My_passw0rd12";

    private final MockMvc mockMvc;
    private final UserJpaRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CaffeineLoginAttemptAdapter loginAttemptAdapter;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private Long userId;

    @Autowired
    MfaTransactionBoundaryIntegrationTest(MockMvc mockMvc, UserJpaRepository userRepository,
            ObjectMapper objectMapper, CaffeineLoginAttemptAdapter loginAttemptAdapter,
            EntityManager entityManager, PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.loginAttemptAdapter = loginAttemptAdapter;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        loginAttemptAdapter.reset("127.0.0.1");
        loginAttemptAdapter.reset("10.0.0.99");
    }

    @AfterEach
    void tearDown() {
        if (userId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id = :userId")
                    .setParameter("userId", userId)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE id = :userId")
                    .setParameter("userId", userId)
                    .executeUpdate();
        });
        userId = null;
    }

    @Test
    @DisplayName("login persists the accepted TOTP counter without a caller transaction")
    void loginWritesReplayCounterWithoutCallerTransaction() throws Exception {
        register();
        String token = login(null);
        userId = userRepository.findByUsername(USERNAME).orElseThrow().getId();

        String base32 = enable(token);
        confirm(token, base32);

        // R-U01: a wrong code fails closed with 401 before the real login.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(wrongCode(base32))))
                .andExpect(status().isUnauthorized());

        String code = currentCode(base32);
        login(code);

        // Same code, same step: the counter committed during the previous
        // (non-transactional) login, so this is a replay and must be rejected.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(code)))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByUsername(USERNAME).orElseThrow()
                .getMfaLastTotpCounter()).isNotNull();
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + USERNAME + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String totpCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(totpCode)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String loginBody(String totpCode) {
        return totpCode == null
                ? "{\"username\": \"" + USERNAME + "\", \"password\": \"" + PASSWORD + "\"}"
                : "{\"username\": \"" + USERNAME + "\", \"password\": \"" + PASSWORD
                        + "\", \"totpCode\": \"" + totpCode + "\"}";
    }

    private String enable(String token) throws Exception {
        MvcResult enrollment = mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(enrollment.getResponse().getContentAsString());
        return body.get("base32Secret").asText();
    }

    private void confirm(String token, String base32) throws Exception {
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + PASSWORD + "\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String currentCode(String base32Secret) {
        byte[] secret = Totp.base32Decode(base32Secret);
        long step = Instant.now().getEpochSecond() / 30L;
        return Totp.generateCode(secret, step, 6);
    }

    private String wrongCode(String base32Secret) {
        String code = currentCode(base32Secret);
        char replacement = code.charAt(0) == '0' ? '1' : '0';
        return replacement + code.substring(1);
    }
}
