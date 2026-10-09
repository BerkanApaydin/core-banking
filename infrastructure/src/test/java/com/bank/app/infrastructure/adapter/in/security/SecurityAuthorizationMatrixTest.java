package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;import org.springframework.context.annotation.Bean;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HIGH-5 regression: the previous {@code SecurityConfigTest} mocked
 * {@code HttpSecurity} with {@code RETURNS_DEEP_STUBS}, so flipping
 * {@code authenticated()} to {@code permitAll()}, disabling CSRF, or opening
 * {@code /api/v1/admin/**} stayed green. This matrix boots the REAL
 * {@link SecurityConfig} + REAL {@link JwtAuthenticationFilter} (with mocked
 * ports underneath) and asserts the endpoint x role matrix over HTTP.
 */
@SpringBootTest(classes = {
        SecurityAuthorizationMatrixTest.TestConfig.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class,
        SecurityAuthorizationMatrixTest.PingController.class
})
@AutoConfigureMockMvc
@ActiveProfiles("security-matrix-test")
class SecurityAuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    // TestApplication-based integration tests scan "com.bank.app" broadly
    // (including test-classes), so this config is gated behind a dedicated
    // profile — the same pattern as SecurityIntegrationTest's
    // "security-test" profile. Without the gate its mock beans (jwtPort,
    // csrfBindingPort, …) would leak into every other IT context and collide
    // with production beans by type.
    @TestConfiguration
    @EnableWebMvc
    @Profile("security-matrix-test")
    static class TestConfig {
        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties(null);
        }

        @Bean
        BrowserSessionCookieProperties browserSessionCookieProperties() {
            return new BrowserSessionCookieProperties(false);
        }

        @Bean
        JwtPort jwtPort() {
            return mock(JwtPort.class);
        }

        @Bean
        TokenBlacklistPort tokenBlacklistPort() {
            TokenBlacklistPort mock = mock(TokenBlacklistPort.class);
            try {
                when(mock.isBlacklisted(anyString())).thenReturn(false);
            } catch (Exception ignored) {
                // Mock setup only.
            }
            return mock;
        }

        @Bean
        LoadUserPort loadUserPort() {
            return mock(LoadUserPort.class);
        }

        @Bean
        CsrfBindingPort csrfBindingPort() {
            return mock(CsrfBindingPort.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return username -> org.springframework.security.core.userdetails.User
                    .withUsername(username).password("{noop}x").authorities("ROLE_USER").build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost:3000"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
            config.setAllowedHeaders(List.of("*"));
            source.registerCorsConfiguration("/**", config);
            return source;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MessageSource messageSource() {
            StaticMessageSource source = new StaticMessageSource();
            source.addMessage("error.unauthorized", Locale.ENGLISH, "Unauthorized");
            source.addMessage("error.access_denied", Locale.ENGLISH, "Access denied.");
            return source;
        }
    }

    @RestController
    @Profile("security-matrix-test")
    static class PingController {
        @GetMapping("/api/v1/admin/ping")
        String adminPing() {
            return "admin-ok";
        }

        @GetMapping("/api/v1/accounts/ping")
        String accountsPing() {
            return "accounts-ok";
        }

        @PostMapping("/api/v1/accounts/ping")
        String accountsPostPing() {
            return "accounts-post-ok";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }

    @Test
    void publicHealthIsPermitAll() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anonymousProtectedGetIs401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousAdminIs401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void userCanAccessOwnResource() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/ping").with(user("u").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void csrfDisabledPostWithoutTokenIsNot403() throws Exception {
        // With Spring CSRF enabled, a POST without a token is 403. Our stateless
        // bearer API disables it (browser CSRF lives in JwtAuthenticationFilter);
        // anonymous POST must therefore be 401 (auth), never 403 (CSRF).
        mockMvc.perform(post("/api/v1/accounts/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersPresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}
