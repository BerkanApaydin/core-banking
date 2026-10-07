package com.bank.app.user.adapter.in.web;

import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.infrastructure.adapter.in.handler.BusinessProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.SecurityProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.RequestProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.ProblemMessageResolver;
import com.bank.app.infrastructure.adapter.in.web.ClientIpResolver;
import com.bank.app.infrastructure.adapter.in.web.ProxyProperties;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null")
@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, BusinessProblemHandler.class, SecurityProblemHandler.class, RequestProblemHandler.class, ProblemMessageResolver.class, ApiVersionConfig.class, ClientIpResolver.class})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerIpResolverTest {

    // These tests verify X-Forwarded-For parsing, which is only honored behind
    // a trusted proxy — so the slice wires the resolver in trusted mode.
    // Profile-guarded like CookieTestConfig: a nested test @Bean in a scanned
    // package must never leak a duplicate into full-context integration tests.
    @TestConfiguration
    @Profile("!test")
    static class TrustedProxyConfig {
        @Bean
        ProxyProperties proxyProperties() {
            return new ProxyProperties(true);
        }
    }

    private final MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    @Autowired
    AuthControllerIpResolverTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @MockitoBean
    private RegisterUserUseCase registerUserPort;

    @MockitoBean
    private LoginUserUseCase loginUserPort;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private RefreshSessionUseCase refreshSessionUseCase;

    @Test
    void shouldUseXForwardedForHeaderWhenPresent() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", "refresh-token", 100L, "testuser", 900000L);

        when(loginUserPort.execute(any(AuthRequest.class), eq("203.0.113.195"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("203.0.113.195"));
    }

    @Test
    void shouldTakeLastIpFromXForwardedForList() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", "refresh-token", 100L, "testuser", 900000L);

        // Append-semantics: the edge proxy appends the peer it saw, so the
        // last entry is trustworthy; leading entries are spoofable.
        when(loginUserPort.execute(any(AuthRequest.class), eq("192.168.1.1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.1, 10.0.0.1, 192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("192.168.1.1"));
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsUnknown() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", "refresh-token", 100L, "testuser", 900000L);

        when(loginUserPort.execute(any(AuthRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("127.0.0.1"));
    }

    @Test
    void shouldUseRemoteAddrWhenNoXForwardedForHeader() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", "refresh-token", 100L, "testuser", 900000L);

        when(loginUserPort.execute(any(AuthRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("127.0.0.1"));
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsEmpty() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", "refresh-token", 100L, "testuser", 900000L);

        when(loginUserPort.execute(any(AuthRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("127.0.0.1"));
    }
}


