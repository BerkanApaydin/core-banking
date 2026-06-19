package com.bank.app.common.security;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    void shouldAllowPublicEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldAllowSwaggerEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowHealthEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectSecuredEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSecuredEndpointsWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessWithValidJwtToken() throws Exception {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("jwt_user");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole("ROLE_USER");
        userRepository.save(user);

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"jwt_user\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginJson).get("token").asText();

        mockMvc.perform(get("/api/v1/accounts")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAccessWithExpiredOrInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.invalid"))
                .andExpect(status().isUnauthorized());
    }
}
