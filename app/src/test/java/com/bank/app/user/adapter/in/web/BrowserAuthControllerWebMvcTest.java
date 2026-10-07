package com.bank.app.user.adapter.in.web;

import com.bank.app.common.application.service.UserContextService;
import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.infrastructure.adapter.in.handler.BusinessProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.SecurityProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.RequestProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.ProblemMessageResolver;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.config.BrowserSessionProperties;
import com.bank.app.user.config.SessionTokenLifetimeProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowserAuthController.class)
@Import({ GlobalExceptionHandler.class, BusinessProblemHandler.class, SecurityProblemHandler.class, RequestProblemHandler.class, ProblemMessageResolver.class, ApiVersionConfig.class, BrowserAuthControllerWebMvcTest.CookieTestConfig.class 
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BrowserAuthController Web MVC")
@SuppressWarnings("null")
class BrowserAuthControllerWebMvcTest {

    /**
     * The controller's cookie lifetimes come from user-owned typed properties
     * (not {@code @Value} placeholders), which a {@code @WebMvcTest} slice
     * does not bind. Provide the same production-equivalent values explicitly
     * so cookie assertions exercise real lifetimes instead of mock defaults.
     *
     * <p>Profile-guarded to the slice: this config class lives in a package
     * covered by the application component scan, so full-context integration
     * tests (profiles {@code test,testcontainers}) would otherwise pick up a
     * second {@code BrowserSessionProperties}/{@code SessionTokenLifetimeProperties}
     * bean next to the bound production ones and fail every context with
     * {@code NoUniqueBeanDefinitionException}. The slice runs without profiles,
     * so {@code "!test"} keeps the beans exactly where they are needed.
     */
    @TestConfiguration
    @Profile("!test")
    static class CookieTestConfig {
        @Bean
        BrowserSessionProperties browserSessionProperties() {
            return new BrowserSessionProperties(false);
        }

        @Bean
        SessionTokenLifetimeProperties sessionTokenLifetimeProperties() {
            return new SessionTokenLifetimeProperties(900000L, 604800000L);
        }
    }

    private final MockMvc mockMvc;

    @Autowired
    BrowserAuthControllerWebMvcTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @MockitoBean
    private LoginUserUseCase loginUserPort;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private RefreshSessionUseCase refreshSessionUseCase;

    @MockitoBean
    private ClientIpResolverPort clientIpResolver;

    @MockitoBean
    private com.bank.app.user.application.port.out.CsrfBindingPort csrfBinding;

    @MockitoBean
    private com.bank.app.user.application.port.out.JwtPort jwtPort;

    @MockitoBean
    private UserContextService userContextService;

    @Nested
    @DisplayName("GET /api/v1/auth/browser/session")
    class Session {

        @Test
        @DisplayName("should return 200 when a principal is present")
        void shouldReturn200WhenAuthenticated() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
            when(userContextService.getCurrentUsername()).thenReturn(Optional.of("alice"));

            mockMvc.perform(get("/api/v1/auth/browser/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(7L))
                    .andExpect(jsonPath("$.username").value("alice"));
        }

        @Test
        @DisplayName("should return 401 when no principal is present")
        void shouldReturn401WhenAnonymous() throws Exception {
            // Absent principal is "not logged in", not a server error: the old
            // orElseThrow() fell through to the 500 fallback.
            when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());
            when(userContextService.getCurrentUsername()).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/auth/browser/session"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/browser/refresh")
    class Refresh {

        private static final String CSRF = "x".repeat(43);

        @Test
        @DisplayName("should rotate cookies on valid refresh token")
        void shouldRotateOnValidRefresh() throws Exception {
            // K7/D8: refresh CSRF is bound to the verified user id.
            when(jwtPort.verifyAndDecode("old-refresh")).thenReturn(
                    new com.bank.app.user.application.port.out.JwtPort.VerifiedToken(
                            "alice", 7L, "ROLE_USER", "jti", System.currentTimeMillis() + 60_000));
            when(csrfBinding.verifyCsrfToken(CSRF, CSRF, "7")).thenReturn(true);
            when(refreshSessionUseCase.execute("old-refresh")).thenReturn(
                    new AuthResponse("new-access", "new-refresh", 7L, "alice", 900000L));

            mockMvc.perform(post("/api/v1/auth/browser/refresh")
                            .cookie(new Cookie("BANK_REFRESH", "old-refresh"),
                                    new Cookie("BANK_CSRF", CSRF))
                            .header("X-CSRF-Token", CSRF))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(header().stringValues("Set-Cookie",
                            hasItem(startsWith("BANK_SESSION="))));

            verify(refreshSessionUseCase).execute("old-refresh");
        }

        @Test
        @DisplayName("should return 403 without CSRF header")
        void shouldRequireCsrf() throws Exception {
            mockMvc.perform(post("/api/v1/auth/browser/refresh")
                            .cookie(new Cookie("BANK_REFRESH", "old-refresh"),
                                    new Cookie("BANK_CSRF", CSRF)))
                    .andExpect(status().isForbidden());

            verify(refreshSessionUseCase, never()).execute(anyString());
        }

        @Test
        @DisplayName("should return 403 when CSRF is not bound to the token identity (K7/D8)")
        void shouldRejectUnboundCsrf() throws Exception {
            when(jwtPort.verifyAndDecode("old-refresh")).thenReturn(
                    new com.bank.app.user.application.port.out.JwtPort.VerifiedToken(
                            "alice", 7L, "ROLE_USER", "jti", System.currentTimeMillis() + 60_000));
            when(csrfBinding.verifyCsrfToken(CSRF, CSRF, "7")).thenReturn(false);

            mockMvc.perform(post("/api/v1/auth/browser/refresh")
                            .cookie(new Cookie("BANK_REFRESH", "old-refresh"),
                                    new Cookie("BANK_CSRF", CSRF))
                            .header("X-CSRF-Token", CSRF))
                    .andExpect(status().isForbidden());

            verify(refreshSessionUseCase, never()).execute(anyString());
        }

        @Test
        @DisplayName("should return 401 without refresh cookie")
        void shouldRequireRefreshCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/browser/refresh")
                            .cookie(new Cookie("BANK_CSRF", CSRF))
                            .header("X-CSRF-Token", CSRF))
                    .andExpect(status().isUnauthorized());
        }
    }
}
