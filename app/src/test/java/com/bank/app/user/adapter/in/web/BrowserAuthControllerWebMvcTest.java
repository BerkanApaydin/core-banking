package com.bank.app.user.adapter.in.web;

import com.bank.app.common.application.service.UserContextService;
import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowserAuthController.class)
@Import({ GlobalExceptionHandler.class, ApiVersionConfig.class })
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BrowserAuthController Web MVC")
@SuppressWarnings("null")
class BrowserAuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUserUseCase loginUserPort;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private ClientIpResolverPort clientIpResolver;

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
}
