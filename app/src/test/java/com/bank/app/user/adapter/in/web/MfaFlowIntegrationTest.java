package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.infrastructure.adapter.out.security.CaffeineLoginAttemptAdapter;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MFA (TOTP) + password-reset flows against real PostgreSQL:
 * enrollment, confirmation gating, code-enforced login and single-use reset.
 * TOTP codes are computed in-test with the same {@code Totp} core the
 * production path verifies (shared host clock, ±1 skew absorbs ms drift).
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
@DisplayName("MFA + password reset integration")
class MfaFlowIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final CaffeineLoginAttemptAdapter loginAttemptAdapter;
    private final PasswordResetTokenPort resetTokenPort;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    MfaFlowIntegrationTest(MockMvc mockMvc, UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder, ObjectMapper objectMapper,
            CaffeineLoginAttemptAdapter loginAttemptAdapter,
            PasswordResetTokenPort resetTokenPort, EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.loginAttemptAdapter = loginAttemptAdapter;
        this.resetTokenPort = resetTokenPort;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        loginAttemptAdapter.reset("127.0.0.1");
        loginAttemptAdapter.reset("10.0.0.99");
    }

    /**
     * This class must NOT be {@code @Transactional}: login is deliberately
     * non-transactional, and a test-managed transaction would mask the
     * REQUIRES_NEW boundary that persists the TOTP replay counter (R-U01).
     * Committed fixtures are therefore removed explicitly.
     */
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id IN "
                            + "(SELECT id FROM users WHERE username IN "
                            + "('mfa_alice', 'reset_bob', 'mfa_carol', 'mfa_dave'))")
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE username IN "
                            + "('mfa_alice', 'reset_bob', 'mfa_carol', 'mfa_dave')")
                    .executeUpdate();
        });
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password, String totpCode) throws Exception {
        return loginPayload(username, password, totpCode).get("token").asText();
    }

    private JsonNode loginPayload(String username, String password, String totpCode) throws Exception {
        String body = totpCode == null
                ? "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"
                : "{\"username\": \"" + username + "\", \"password\": \"" + password
                        + "\", \"totpCode\": \"" + totpCode + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String currentCode(String base32Secret) {
        byte[] secret = Totp.base32Decode(base32Secret);
        long step = Instant.now().getEpochSecond() / 30L;
        return Totp.generateCode(secret, step, 6);
    }

    /**
     * The code for the next step. The verifier tolerates ±1 skew, so this is
     * a legitimate code now while guaranteeing that consuming it advances the
     * stored replay counter past the current step.
     */
    private String nextStepCode(String base32Secret) {
        byte[] secret = Totp.base32Decode(base32Secret);
        long step = Instant.now().getEpochSecond() / 30L + 1;
        return Totp.generateCode(secret, step, 6);
    }

    @Test
    @DisplayName("enrollment stays off until confirmed, then login requires the code")
    void enrollmentGatesLoginOnConfirmation() throws Exception {
        register("mfa_alice", "My_passw0rd12");
        JsonNode firstLogin = loginPayload("mfa_alice", "My_passw0rd12", null);
        String token = firstLogin.get("token").asText();
        String preConfirmRefresh = firstLogin.get("refreshToken").asText();
        long tokenVersionBefore = userRepository.findByUsername("mfa_alice")
                .orElseThrow().getTokenVersion();

        // R-U04: enrollment is a security-policy change, so it must not start
        // without a password step-up.
        mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        MvcResult enrollment = mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(enrollment.getResponse().getContentAsString());
        String base32 = body.get("base32Secret").asText();
        assertThat(base32).hasSize(32);
        assertThat(body.get("otpauthUri").asText()).startsWith("otpauth://totp/");

        // R-U04: confirmation also requires the password step-up; a code
        // alone must never enable the factor.
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"" + currentCode(base32) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByUsername("mfa_alice").orElseThrow().isMfaEnabled()).isFalse();

        // Still off: login without a code keeps working before confirmation.
        login("mfa_alice", "My_passw0rd12", null);

        // A wrong code fails as 400 (not session-killing 401) and leaves MFA off.
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        assertThat(userRepository.findByUsername("mfa_alice").orElseThrow().isMfaEnabled()).isFalse();

        // Password step-up is required: a right code with a wrong password
        // must not enable the factor.
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"Wrong_passw0rd99\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        assertThat(userRepository.findByUsername("mfa_alice").orElseThrow().isMfaEnabled()).isFalse();

        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("mfa_alice")).isPresent();
        assertThat(userRepository.findByUsername("mfa_alice").orElseThrow().isMfaEnabled()).isTrue();

        // R-U05: enabling MFA is an authentication-policy change. It bumps
        // the token generation and revokes every refresh family minted before
        // the change, so an old session cannot outlive it.
        assertThat(userRepository.findByUsername("mfa_alice").orElseThrow().getTokenVersion())
                .isEqualTo(tokenVersionBefore + 1);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + preConfirmRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // Now on: login without a code fails with the generic message...
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"mfa_alice\", \"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        // ...a wrong code fails identically (no oracle)...
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"mfa_alice\", \"password\": \"My_passw0rd12\","
                                + " \"totpCode\": \"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        // ...and the current code logs in.
        login("mfa_alice", "My_passw0rd12", currentCode(base32));
    }

    @Test
    @DisplayName("R-U08: wrong MFA management passwords share the account lockout budget")
    void wrongManagementPasswordIsRateLimited() throws Exception {
        register("mfa_alice", "My_passw0rd12");
        String token = login("mfa_alice", "My_passw0rd12", null);

        // Five wrong passwords exhaust app.security.mfa.max-attempts.
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/mfa/enable")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Wrong_passw0rd99\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        }

        // The next request is rejected before password verification, even
        // with the correct password: the password step-up is budgeted.
        mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FAILED_MFA_ATTEMPTS"));
    }

    @Test
    @DisplayName("password reset is enumeration-safe and single-use")
    void passwordResetIsSingleUse() throws Exception {
        register("reset_bob", "My_passw0rd12");

        // Unknown users get the same 202 (no enumeration).
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"no_such_user\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"reset_bob\"}"))
                .andExpect(status().isAccepted());

        // Unknown and reused tokens fail identically (400: the confirm
        // endpoint is public and the UI treats 401 as session expiry).
        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"bogus\", \"newPassword\": \"New_passw0rd34\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        // The simulation channel is the only reader of the raw token; drive
        // the confirm path through a directly-issued token instead. V58 allows
        // only one live token per user, so retire the request token first,
        // in the same transaction as the direct issue.
        UserJpaEntity bob = userRepository.findByUsername("reset_bob").orElseThrow();
        String raw = "test-reset-token-value";
        transactionTemplate.executeWithoutResult(status -> {
            resetTokenPort.deleteByUserId(bob.getId());
            resetTokenPort.issue(new PasswordResetTokenPort.PasswordResetToken(
                    TokenDigest.sha256Hex(raw), bob.getId(),
                    LocalDateTime.now().plusMinutes(15), null));
        });

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + raw + "\", \"newPassword\": \"New_passw0rd34\"}"))
                .andExpect(status().isNoContent());

        // Reuse fails like unknown...
        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + raw + "\", \"newPassword\": \"New_passw0rd34\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        // ...and the new password logs in.
        login("reset_bob", "New_passw0rd34", null);
        assertThat(passwordEncoder.matches("New_passw0rd34",
                userRepository.findByUsername("reset_bob").orElseThrow().getPassword())).isTrue();
    }

    @Test
    @DisplayName("MFA disable requires password and code")
    void mfaDisableRequiresBothFactors() throws Exception {
        register("mfa_carol", "My_passw0rd12");
        String token = login("mfa_carol", "My_passw0rd12", null);

        MvcResult enrollment = mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String base32 = objectMapper.readTree(enrollment.getResponse().getContentAsString())
                .get("base32Secret").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isNoContent());

        // Wrong password fails even with a right code — as 400, not 401: the
        // session is valid, so a 401 would make the UI log the user out.
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"Wrong_passw0rd99\", \"code\": \"" + currentCode(base32) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        assertThat(userRepository.findByUsername("mfa_carol").orElseThrow().isMfaEnabled()).isTrue();

        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \"" + currentCode(base32) + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findByUsername("mfa_carol").orElseThrow().isMfaEnabled()).isFalse();

        // Factor gone: plain login works again.
        login("mfa_carol", "My_passw0rd12", null);
    }

    @Test
    @DisplayName("disable clears the TOTP replay counter so re-enrollment works in the same step")
    void disableClearsReplayCounterForReenrollment() throws Exception {
        register("mfa_dave", "My_passw0rd12");
        String token = login("mfa_dave", "My_passw0rd12", null);

        MvcResult enrollment = mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String base32 = objectMapper.readTree(enrollment.getResponse().getContentAsString())
                .get("base32Secret").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isNoContent());

        // A real login advances the persisted TOTP step counter.
        login("mfa_dave", "My_passw0rd12", currentCode(base32));
        assertThat(userRepository.findByUsername("mfa_dave").orElseThrow()
                .getMfaLastTotpCounter()).isNotNull();

        // Consuming a next-step code pushes the stored counter ahead of the
        // wall clock, so the post-enrollment login below is deterministically
        // in the counter's shadow if disable leaves it behind.
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + nextStepCode(base32) + "\"}"))
                .andExpect(status().isNoContent());

        // The counter belongs to the deleted secret. Keeping it would make a
        // fresh secret's code look like a replay when enable+confirm+login
        // happen inside the same 30-second step (or right after a skew code).
        assertThat(userRepository.findByUsername("mfa_dave").orElseThrow()
                .getMfaLastTotpCounter()).isNull();

        String plainToken = login("mfa_dave", "My_passw0rd12", null);
        MvcResult reenrollment = mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String newBase32 = objectMapper.readTree(reenrollment.getResponse().getContentAsString())
                .get("base32Secret").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + currentCode(newBase32) + "\"}"))
                .andExpect(status().isNoContent());

        login("mfa_dave", "My_passw0rd12", currentCode(newBase32));
    }

    @Test
    @DisplayName("R-T12: wrong MFA disable passwords share the account lockout budget")
    void wrongDisablePasswordIsRateLimited() throws Exception {
        register("mfa_carol", "My_passw0rd12");
        String token = login("mfa_carol", "My_passw0rd12", null);
        MvcResult enrollment = mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String base32 = objectMapper.readTree(enrollment.getResponse().getContentAsString())
                .get("base32Secret").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isNoContent());

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/mfa/disable")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Wrong_passw0rd99\", \"code\": \""
                                    + currentCode(base32) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        }

        // The sixth call is rejected by the shared budget before the password
        // is checked — even with both correct factors.
        mockMvc.perform(post("/api/v1/auth/mfa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"My_passw0rd12\", \"code\": \""
                                + currentCode(base32) + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FAILED_MFA_ATTEMPTS"));
        assertThat(userRepository.findByUsername("mfa_carol").orElseThrow().isMfaEnabled()).isTrue();
    }
}
