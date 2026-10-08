package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.user.application.dto.AuthRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full refresh lifecycle through HTTP: pair issuance, rotation, reuse
// detection (family kill) and logout revocation.
@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
class RefreshTokenFlowIT extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    @Autowired
    RefreshTokenFlowIT(MockMvc mockMvc, ObjectMapper objectMapper,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    private static final String PASSWORD = "RefreshFlow123";

    private String username() {
        return "refresh_" + UUID.randomUUID().toString().replace("-", "");
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
}
