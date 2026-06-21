package com.bank.app.user.infrastructure.web;

import com.bank.app.common.api.ApiVersionConfig;
import com.bank.app.common.handler.GlobalExceptionHandler;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.infrastructure.security.FailedLoginAttemptService;
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

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, ApiVersionConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegisterUserUseCase registerUserPort;

    @MockitoBean
    private LoginUserUseCase loginUserPort;

    @MockitoBean
    private FailedLoginAttemptService failedLoginAttemptService;

    @Test
    void shouldRegisterAndReturn201() throws Exception {
        AuthRequest request = new AuthRequest("newuser", "Password1");

        doNothing().when(registerUserPort).execute(any(AuthRequest.class));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn400WhenRegisterRequestIsInvalid() throws Exception {
        AuthRequest request = new AuthRequest("", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLoginAndReturn200() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("jwt-token", 100L, "testuser");

        when(failedLoginAttemptService.isBlocked("127.0.0.1")).thenReturn(false);
        doNothing().when(failedLoginAttemptService).reset("127.0.0.1");
        when(loginUserPort.execute(any(AuthRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value(100L))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void shouldReturn429WhenIpIsBlocked() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");

        when(failedLoginAttemptService.isBlocked("127.0.0.1")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }
}
