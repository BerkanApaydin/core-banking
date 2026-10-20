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
                            + "('mfa_alice', 'reset_bob', 'mfa_carol'))")
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE username IN "
                            + "('mfa_alice', 'reset_bob', 'mfa_carol')")
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
        String body = totpCode == null
                ? "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"
                : "{\"username\": \"" + username + "\", \"password\": \"" + password
                        + "\", \"totpCode\": \"" + totpCode + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String currentCode(String base32Secret) {
        byte[] secret = Totp.base32Decode(base32Secret);
        long step = Instant.now().getEpochSecond() / 30L;
        return Totp.generateCode(secret, step, 6);
    }

    @Test
    @DisplayName("enrollment stays off until confirmed, then login requires the code")
    void enrollmentGatesLoginOnConfirmation() throws Exception {
        register("mfa_alice", "My_passw0rd12");
        String token = login("mfa_alice", "My_passw0rd12", null);

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
        // the confirm path through a directly-issued token instead.
        UserJpaEntity bob = userRepository.findByUsername("reset_bob").orElseThrow();
        String raw = "test-reset-token-value";
        resetTokenPort.issue(new PasswordResetTokenPort.PasswordResetToken(
                TokenDigest.sha256Hex(raw), bob.getId(),
                LocalDateTime.now().plusMinutes(15), null));

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
}
