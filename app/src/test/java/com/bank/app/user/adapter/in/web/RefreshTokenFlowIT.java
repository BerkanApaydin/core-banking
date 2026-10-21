package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.adapter.out.persistence.RefreshTokenJpaEntity;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full refresh lifecycle through HTTP: pair issuance, rotation, reuse
// detection (family kill) and logout revocation.
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
class RefreshTokenFlowIT extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    private final EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;

    private final RefreshTokenPort refreshTokenPort;

    private final List<String> createdUsernames = new ArrayList<>();

    @Autowired
    RefreshTokenFlowIT(MockMvc mockMvc, ObjectMapper objectMapper, EntityManager entityManager,
            PlatformTransactionManager transactionManager, RefreshTokenPort refreshTokenPort,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.refreshTokenPort = refreshTokenPort;
    }

    private static final String PASSWORD = "RefreshFlow123";

    /**
     * This class must NOT be {@code @Transactional}: revocation runs through
     * REQUIRES_NEW, so a test-managed transaction would hide the committed
     * refresh-token rows from the family-revoke transaction.
     */
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

    private String username() {
        String username = "refresh_" + UUID.randomUUID().toString().replace("-", "");
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
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String refresh(String refreshToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("refreshToken").asText();
    }

    @Test
    void shouldRotateAndDetectReuse() throws Exception {
        JsonNode pair = login(username());
        String firstRefresh = pair.get("refreshToken").asText();
        assertNotNull(firstRefresh);

        String secondRefresh = refresh(firstRefresh);
        assertNotEquals(firstRefresh, secondRefresh);

        // Replay of the rotated token: theft response, whole family dies.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isUnauthorized());

        // The replacement died with its family too.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPreserveRotationLinkWhenRevokingAnAlreadyRotatedToken() throws Exception {
        JsonNode pair = login(username());
        String oldHash = TokenDigest.sha256Hex(pair.get("refreshToken").asText());
        String replacementHash = TokenDigest.sha256Hex("concurrent-replacement");

        // R-U09: rotation and revocation race in the same transaction. The
        // revocation must not overwrite the replacedByHash link written by
        // rotation; otherwise reuse detection loses the token chain.
        transactionTemplate.executeWithoutResult(status -> {
            refreshTokenPort.markRotated(oldHash, replacementHash);
            refreshTokenPort.revoke(oldHash);
        });

        RefreshTokenJpaEntity stored = entityManager.find(RefreshTokenJpaEntity.class, oldHash);
        assertNotNull(stored);
        assertTrue(stored.isRevoked());
        assertEquals(replacementHash, stored.getReplacedByHash());
    }

    @Test
    void shouldRejectAccessTokenAsRefresh() throws Exception {
        JsonNode pair = login(username());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", pair.get("token").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectGarbageRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", "not-a-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRevokeRefreshOnLogout() throws Exception {
        String user = username();
        JsonNode pair = login(user);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + pair.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", pair.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", pair.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRevokeWholeFamilyOnLogoutWithAlreadyRotatedToken() throws Exception {
        JsonNode pair = login(username());
        String firstRefresh = pair.get("refreshToken").asText();
        String secondRefresh = refresh(firstRefresh);

        // The browser may still hold the rotated token after a failed refresh.
        // Logging out with it must revoke the family, including the successor
        // the rotation already issued — otherwise the replacement survives
        // logout for its full lifetime (R-T04).
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + pair.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());
    }
}
