package com.bank.app.user.adapter.in.web;

import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.infrastructure.adapter.in.web.ClientIpResolver;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null")
@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, ApiVersionConfig.class, ClientIpResolver.class})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerIpResolverTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegisterUserUseCase registerUserPort;

    @MockitoBean
    private LoginUserUseCase loginUserPort;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @Test
    void shouldUseXForwardedForHeaderWhenPresent() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", 100L, "testuser");

        when(loginUserPort.execute(any(AuthRequest.class), eq("203.0.113.195"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("203.0.113.195"));
    }

    @Test
    void shouldTakeFirstIpFromXForwardedForList() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", 100L, "testuser");

        when(loginUserPort.execute(any(AuthRequest.class), eq("198.51.100.1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.1, 10.0.0.1, 192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("198.51.100.1"));
    }

    @Test
    void shouldUseRemoteAddrWhenXForwardedForIsUnknown() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", 100L, "testuser");

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
        AuthResponse response = new AuthResponse("jwt-token", 100L, "testuser");

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
        AuthResponse response = new AuthResponse("jwt-token", 100L, "testuser");

        when(loginUserPort.execute(any(AuthRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(loginUserPort).execute(any(AuthRequest.class), eq("127.0.0.1"));
    }
}


