package com.bank.app.user.adapter.in.web;

import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.web.ClientIpResolver;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.infrastructure.adapter.in.handler.BusinessProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.SecurityProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.RequestProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.ProblemMessageResolver;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.MfaStatusUseCase;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.in.ConfirmPasswordResetUseCase;
import com.bank.app.user.application.port.in.DisableMfaUseCase;
import com.bank.app.user.application.port.in.EnableMfaUseCase;
import com.bank.app.user.application.port.in.RequestPasswordResetUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, RegistrationController.class, MfaController.class})
@Import({ GlobalExceptionHandler.class, BusinessProblemHandler.class, SecurityProblemHandler.class, RequestProblemHandler.class, ProblemMessageResolver.class, ApiVersionConfig.class })
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Web MVC")
@SuppressWarnings("null")
class AuthControllerWebMvcTest {

    private final MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    @Autowired
    AuthControllerWebMvcTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @MockitoBean
    private RegisterUserUseCase registerUserPort;

    @MockitoBean
    private LoginUserUseCase loginUserPort;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private RefreshSessionUseCase refreshSessionUseCase;

    @MockitoBean
    private MfaStatusUseCase mfaStatusUseCase;

    @MockitoBean
    private EnableMfaUseCase enableMfaUseCase;

    @MockitoBean
    private ConfirmMfaUseCase confirmMfaUseCase;

    @MockitoBean
    private DisableMfaUseCase disableMfaUseCase;

    @MockitoBean
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockitoBean
    private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @MockitoBean
    private UserContextService userContextService;

    @BeforeEach
    void setUp() {
        when(clientIpResolver.resolveClientIp(any(), any())).thenReturn("1.2.3.4");
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("should return 201 when registration is valid")
        void shouldReturn201() throws Exception {
            // Must satisfy the live password policy (@ValidPassword): the old
            // "Password1234" fixture is denylisted and now fails at the web
            // boundary with 400 before the (mocked) use case runs.
            AuthRequest request = new AuthRequest("newuser", "Secret123456");

            doNothing().when(registerUserPort).execute(any(AuthRequest.class));

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400WhenInvalid() throws Exception {
            // Raw JSON: the application AuthRequest now rejects blanks at
            // construction, so an invalid wire payload must bypass it.
            String body = "{\"username\": \"\", \"password\": \"\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
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
        @DisplayName("should return 400 when password is below the 12-char minimum")
        void shouldReturn400WhenPasswordBelowMinimum() throws Exception {
            String body = "{\"username\": \"validuser\", \"password\": \"Short123456\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        @DisplayName("should propagate business exception on duplicate username")
        void shouldPropagateBusinessException() throws Exception {
            AuthRequest request = new AuthRequest("existing", "Secret123456");

            doThrow(new DuplicateIbanException("error.username_exists", new Object[] {},
                    "Username already in use"))
                    .when(registerUserPort).execute(any(AuthRequest.class));

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 400 when password is shorter than policy minimum")
        void shouldReturn400WhenPasswordTooShort() throws Exception {
            String body = "{\"username\": \"newuser\", \"password\": \"Short1\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").exists());

            verifyNoInteractions(registerUserPort);
        }

        @Test
        @DisplayName("should return 400 when registration username contains glob characters")
        void shouldReturn400WhenUsernameContainsGlobCharacters() throws Exception {
            String body = "{\"username\": \"bad*name\", \"password\": \"ValidPassword123\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").exists());

            verifyNoInteractions(registerUserPort);
        }

        @Test
        @DisplayName("should return 400 when email is invalid")
        void shouldReturn400WhenEmailInvalid() throws Exception {
            AuthRequest request = new AuthRequest("validuser", "ValidPass1", "not-an-email", "5551234567");

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").exists());
        }

        @Test
        @DisplayName("should return 400 when request body is malformed")
        void shouldReturn400OnMalformedBody() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                    .andExpect(status().isBadRequest());
        }

    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("should return 200 when credentials are valid")
        void shouldReturn200() throws Exception {
            AuthRequest request = new AuthRequest("testuser", "password");
            AuthResponse response = new AuthResponse("jwt-token", "refresh-token", 100L, "testuser", 900000L);

            when(loginUserPort.execute(any(AuthRequest.class), anyString())).thenReturn(response);

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

            when(loginUserPort.execute(any(AuthRequest.class), anyString()))
                    .thenThrow(new TooManyFailedLoginAttemptsException("Too many attempts"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void shouldReturn503WhenLoginAttemptStoreIsUnavailable() throws Exception {
            AuthRequest request = new AuthRequest("testuser", "password");
            when(loginUserPort.execute(any(AuthRequest.class), anyString()))
                    .thenThrow(new LoginAttemptStoreUnavailableException(new RuntimeException("Redis down")));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("SECURITY_BACKEND_UNAVAILABLE"))
                    .andExpect(jsonPath("$.detail").value("Security service temporarily unavailable. Please try again later."));
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
        @DisplayName("should return 400 when login with blank username")
        void shouldReturn400WhenLoginWithBlankUsername() throws Exception {
            String body = "{\"username\": \"\", \"password\": \"password\"}";

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should propagate authentication exception")
        void shouldPropagateAuthException() throws Exception {
            AuthRequest request = new AuthRequest("nobody", "wrong");

            when(loginUserPort.execute(any(AuthRequest.class), anyString()))
                    .thenThrow(new BadCredentialsException("Invalid username or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("should return 204 when logout is called")
        void shouldReturn204OnLogout() throws Exception {
            doNothing().when(logoutUseCase).execute(any(String.class), any());

            mockMvc.perform(post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer some-jwt-token"))
                    .andExpect(status().isNoContent());

            verify(logoutUseCase).execute("Bearer some-jwt-token", null);
        }

        @Test
        @DisplayName("should forward optional refresh token on logout")
        void shouldForwardRefreshTokenOnLogout() throws Exception {
            doNothing().when(logoutUseCase).execute(any(String.class), any());

            mockMvc.perform(post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer some-jwt-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\": \"some-refresh-token\"}"))
                    .andExpect(status().isNoContent());

            verify(logoutUseCase).execute("Bearer some-jwt-token", "some-refresh-token");
        }

        @Test
        @DisplayName("should rotate session on valid refresh token")
        void shouldRotateOnValidRefreshToken() throws Exception {
            when(refreshSessionUseCase.execute("good-refresh"))
                    .thenReturn(new AuthResponse("new-access", "new-refresh", 7L, "alice", 900000L));

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\": \"good-refresh\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("new-access"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh"));

            verify(refreshSessionUseCase).execute("good-refresh");
        }

        @Test
        @DisplayName("should return 401 when Authorization header is missing")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            // MissingRequestHeaderException used to fall through to the 500
            // fallback; it now maps to 401 AUTHENTICATION_FAILED.
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

            verifyNoInteractions(logoutUseCase);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/mfa/*")
    class Mfa {

        @Test
        @DisplayName("should return enrollment status for an authenticated user")
        void shouldReturnStatus() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
            when(mfaStatusUseCase.isEnabled(7L)).thenReturn(true);

            mockMvc.perform(get("/api/v1/auth/mfa/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mfaEnabled").value(true));

            verify(mfaStatusUseCase).isEnabled(7L);
        }

        @Test
        @DisplayName("should return 401 on status without authentication")
        void shouldReturn401OnStatusWithoutAuth() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/auth/mfa/status"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(mfaStatusUseCase);
        }

        @Test
        @DisplayName("should return enrollment on enable for an authenticated user")
        void shouldReturnEnrollmentOnEnable() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
            when(enableMfaUseCase.execute(7L, "Secret123")).thenReturn(
                    new MfaEnrollment(
                            "otpauth://totp/x", "JBSWY3DPEHPK3PXP"));

            mockMvc.perform(post("/api/v1/auth/mfa/enable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Secret123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.base32Secret").value("JBSWY3DPEHPK3PXP"));

            verify(enableMfaUseCase).execute(7L, "Secret123");
        }

        @Test
        @DisplayName("should return 401 on enable without authentication")
        void shouldReturn401OnEnableWithoutAuth() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/auth/mfa/enable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Secret123\"}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(enableMfaUseCase);
        }

        @Test
        @DisplayName("should return 204 on confirm")
        void shouldReturn204OnConfirm() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
            doNothing().when(confirmMfaUseCase).execute(eq(7L), eq("Secret123"), eq("123456"));

            mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Secret123\", \"code\": \"123456\"}"))
                    .andExpect(status().isNoContent());

            verify(confirmMfaUseCase).execute(7L, "Secret123", "123456");
        }

        @Test
        @DisplayName("should return 400 on confirm with a malformed code")
        void shouldReturn400OnMalformedCode() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));

            mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Secret123\", \"code\": \"abc\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(confirmMfaUseCase);
        }

        @Test
        @DisplayName("should return 204 on disable")
        void shouldReturn204OnDisable() throws Exception {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
            doNothing().when(disableMfaUseCase).execute(eq(7L), eq("Secret123"), eq("123456"));

            mockMvc.perform(post("/api/v1/auth/mfa/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"Secret123\", \"code\": \"123456\"}"))
                    .andExpect(status().isNoContent());

            verify(disableMfaUseCase).execute(7L, "Secret123", "123456");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/password/reset/*")
    class PasswordReset {

        @Test
        @DisplayName("should return 202 on reset request regardless of account existence")
        void shouldReturn202OnRequest() throws Exception {
            doNothing().when(requestPasswordResetUseCase).execute("alice");

            mockMvc.perform(post("/api/v1/auth/password/reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"alice\"}"))
                    .andExpect(status().isAccepted());

            verify(requestPasswordResetUseCase).execute("alice");
        }

        @Test
        @DisplayName("should return 204 on reset confirm")
        void shouldReturn204OnConfirm() throws Exception {
            doNothing().when(confirmPasswordResetUseCase).execute(eq("token-value"), eq("NewStr0ngPass!"));

            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\": \"token-value\", \"newPassword\": \"NewStr0ngPass!\"}"))
                    .andExpect(status().isNoContent());

            verify(confirmPasswordResetUseCase).execute("token-value", "NewStr0ngPass!");
        }
    }
}
