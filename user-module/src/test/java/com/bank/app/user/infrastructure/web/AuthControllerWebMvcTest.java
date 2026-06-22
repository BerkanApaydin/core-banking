package com.bank.app.user.infrastructure.web;

import com.bank.app.common.api.ApiVersionConfig;
import com.bank.app.common.exception.BusinessException;
import com.bank.app.common.handler.GlobalExceptionHandler;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.infrastructure.security.FailedLoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, ApiVersionConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Web MVC")
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

    private static class DuplicateUsernameException extends BusinessException {
        DuplicateUsernameException(String messageKey, Object[] args, String defaultMessage) {
            super(messageKey, args, defaultMessage);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("should return 201 when registration is valid")
        void shouldReturn201() throws Exception {
            AuthRequest request = new AuthRequest("newuser", "Password1");

            doNothing().when(registerUserPort).execute(any(AuthRequest.class));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenInvalid() throws Exception {
            AuthRequest request = new AuthRequest("", "");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").exists());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400OnEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when username is missing")
        void shouldReturn400WhenUsernameMissing() throws Exception {
            String body = "{\"password\": \"ValidPass1\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").exists());
        }

        @Test
        @DisplayName("should return 400 when password is missing")
        void shouldReturn400WhenPasswordMissing() throws Exception {
            String body = "{\"username\": \"validuser\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").exists());
        }

        @Test
        @DisplayName("should propagate business exception on duplicate username")
        void shouldPropagateBusinessException() throws Exception {
            AuthRequest request = new AuthRequest("existing", "Password1");

            doThrow(new DuplicateUsernameException("error.username_exists", new Object[]{},"Kullanıcı adı zaten kullanımda"))
                    .when(registerUserPort).execute(any(AuthRequest.class));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("should return 200 when credentials are valid")
        void shouldReturn200() throws Exception {
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
        @DisplayName("should return 429 when IP is blocked")
        void shouldReturn429WhenIpBlocked() throws Exception {
            AuthRequest request = new AuthRequest("testuser", "password");

            when(failedLoginAttemptService.isBlocked("127.0.0.1")).thenReturn(true);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400OnEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should propagate authentication exception")
        void shouldPropagateAuthException() throws Exception {
            AuthRequest request = new AuthRequest("nobody", "wrong");

            when(failedLoginAttemptService.isBlocked("127.0.0.1")).thenReturn(false);
            when(loginUserPort.execute(any(AuthRequest.class)))
                    .thenThrow(new BadCredentialsException("Geçersiz kullanıcı adı veya şifre"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
