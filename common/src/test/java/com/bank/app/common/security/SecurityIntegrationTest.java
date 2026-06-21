package com.bank.app.common.security;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles({"test", "testcontainers", "security-test"})
@Import({SecurityIntegrationTest.TestSecurityConfig.class, SecurityIntegrationTest.TestController.class})
class SecurityIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @RestController
    @Profile("security-test")
    static class TestController {
        @GetMapping("/api/test/secured")
        public String secured() {
            return "secured";
        }
    }

    @Configuration
    @Profile("security-test")
    static class TestSecurityConfig {
        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                User.withUsername("testuser")
                    .password("{noop}password")
                    .roles("USER")
                    .build()
            );
        }
    }

    @Test
    void shouldAllowPublicEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectSecuredEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/secured"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSecuredEndpointsWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/test/secured")
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessWithValidJwtToken() throws Exception {
        String token = jwtTokenProvider.generateToken(1L, "testuser");

        mockMvc.perform(get("/api/test/secured")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAccessWithExpiredOrInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/api/test/secured")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.invalid"))
                .andExpect(status().isUnauthorized());
    }
}
