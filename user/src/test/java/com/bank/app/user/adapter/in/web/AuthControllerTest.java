package com.bank.app.user.adapter.in.web;

import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaConfirmWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaDisableWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaEnableWebRequest;
import com.bank.app.user.adapter.in.web.dto.PasswordResetConfirmWebRequest;
import com.bank.app.user.adapter.in.web.dto.PasswordResetRequestWebRequest;
import com.bank.app.user.adapter.in.web.dto.RefreshTokenWebRequest;
import com.bank.app.user.adapter.in.web.dto.RegisterWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.MfaStatusUseCase;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.in.ConfirmPasswordResetUseCase;
import com.bank.app.user.application.port.in.DisableMfaUseCase;
import com.bank.app.user.application.port.in.EnableMfaUseCase;
import com.bank.app.user.application.port.in.RequestPasswordResetUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * In-module slice for the user web adapter.
 *
 * <p>
 * PIT reported the whole controller as NO_COVERAGE because the MockMvc
 * tests live in the {@code app} module, outside this module's
 * {@code targetTests}. These standalone MockMvc tests kill the return-value
 * and conditional mutants locally.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

        @Mock
        private RegisterUserUseCase registerUserUseCase;
        @Mock
        private LoginUserUseCase loginUserUseCase;
        @Mock
        private ClientIpResolverPort clientIpResolver;
        @Mock
        private LogoutUseCase logoutUseCase;
        @Mock
        private RefreshSessionUseCase refreshSessionUseCase;
        @Mock
        private MfaStatusUseCase mfaStatusUseCase;
        @Mock
        private EnableMfaUseCase enableMfaUseCase;
        @Mock
        private ConfirmMfaUseCase confirmMfaUseCase;
        @Mock
        private DisableMfaUseCase disableMfaUseCase;
        @Mock
        private RequestPasswordResetUseCase requestPasswordResetUseCase;
        @Mock
        private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
        @Mock
        private UserContextService userContextService;

        private MockMvc mockMvc;
        private AuthController controller;
        private MfaController mfaController;
        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                controller = new AuthController(
                                loginUserUseCase, clientIpResolver, logoutUseCase, refreshSessionUseCase);
                RegistrationController registrationController = new RegistrationController(
                                registerUserUseCase, requestPasswordResetUseCase, confirmPasswordResetUseCase);
                mfaController = new MfaController(mfaStatusUseCase, enableMfaUseCase, confirmMfaUseCase,
                                disableMfaUseCase, userContextService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller, registrationController, mfaController)
                                .setControllerAdvice(new AuthenticationRequiredAdvice())
                                .build();
        }

        @org.springframework.web.bind.annotation.RestControllerAdvice
        static class AuthenticationRequiredAdvice {
                @org.springframework.web.bind.annotation.ExceptionHandler(com.bank.app.common.application.exception.AuthenticationRequiredException.class)
                org.springframework.http.ResponseEntity<Void> handle() {
                        return org.springframework.http.ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                }
        }

        @Test
        void shouldRegisterWithCreated() throws Exception {
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RegisterWebRequest(
                                                "alice", "Str0ngPassw0rd!", "alice@example.com", "+905551112233"))))
                                .andExpect(status().isCreated());

                verify(registerUserUseCase).execute(any(AuthRequest.class));
        }

        @Test
        void shouldLoginWithNoStoreCache() throws Exception {
                when(clientIpResolver.resolveClientIp(any(), any())).thenReturn("10.0.0.1");
                when(loginUserUseCase.execute(any(AuthRequest.class), eq("10.0.0.1")))
                                .thenReturn(new AuthResponse("access", "refresh", 7L, "alice", 900000L));

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AuthWebRequest("alice", "Secret123456"))))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store"))
                                .andExpect(jsonPath("$.token").value("access"));
        }

        @Test
        void shouldRefreshWithNoStoreCache() throws Exception {
                when(refreshSessionUseCase.execute("rt-1"))
                                .thenReturn(new AuthResponse("a2", "r2", 7L, "alice", 900000L));

                mockMvc.perform(post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RefreshTokenWebRequest("rt-1"))))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store"))
                                .andExpect(jsonPath("$.token").value("a2"));
        }

        @Test
        void shouldRejectAnonymousLogout() throws Exception {
                mockMvc.perform(post("/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Forwarded-For", "10.0.0.1"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRejectWhitespaceOnlyAuthorizationHeader() throws Exception {
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "   ")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isUnauthorized());

                verify(logoutUseCase, never()).execute(any(), any());
        }

        @Test
        void shouldRejectANonBearerAuthorizationHeader() throws Exception {
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Basic dXNlcjpwdw==")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isUnauthorized());

                verify(logoutUseCase, never()).execute(any(), any());
        }

        @Test
        void shouldRejectAnEmptyBearerCredential() throws Exception {
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Bearer    ")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isUnauthorized());

                verify(logoutUseCase, never()).execute(any(), any());
        }

        @Test
        void shouldRejectBlankRefreshTokenBodyOnDirectInvocation() {
                assertThatThrownBy(() -> controller.logout(null, new RefreshTokenWebRequest("   ")))
                                .isInstanceOf(com.bank.app.common.application.exception.AuthenticationRequiredException.class);
                verify(logoutUseCase, never()).execute(any(), any());
        }

        @Test
        void shouldLogoutWithBearerHeader() throws Exception {
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Bearer tok-1")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNoContent());

                verify(logoutUseCase).execute(eq("Bearer tok-1"), eq(null));
        }

        @Test
        void shouldLogoutWithRefreshBody() throws Exception {
                mockMvc.perform(post("/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RefreshTokenWebRequest("rt-9"))))
                                .andExpect(status().isNoContent());

                verify(logoutUseCase).execute(eq(null), eq("rt-9"));
        }

        @Test
        void shouldReturnMfaStatusForTheCurrentUser() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
                when(mfaStatusUseCase.isEnabled(7L)).thenReturn(true);

                mockMvc.perform(get("/auth/mfa/status"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store"))
                                .andExpect(jsonPath("$.mfaEnabled").value(true));
        }

        @Test
        void shouldRejectAnonymousMfaStatus() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

                mockMvc.perform(get("/auth/mfa/status"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReportAMissingUserAsNotFoundOnMfaStatus() {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
                when(mfaStatusUseCase.isEnabled(7L)).thenThrow(new UserNotFoundException("User not found: 7"));

                // NOT_FOUND maps to 404. It must not be masked as an authentication
                // failure (401), which would be indistinguishable from "not logged in".
                assertThatThrownBy(() -> mfaController.mfaStatus())
                                .isInstanceOf(UserNotFoundException.class)
                                .satisfies(thrown -> assertThat(((UserNotFoundException) thrown).getFailureKind())
                                                .isEqualTo(BusinessFailureKind.NOT_FOUND));
        }

        @Test
        void shouldStartEnrollmentForTheCurrentUser() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
                when(enableMfaUseCase.execute(7L, "Str0ngPassw0rd!"))
                                .thenReturn(new MfaEnrollment("otpauth://totp/x", "BASE32SECRET"));

                mockMvc.perform(post("/auth/mfa/enable")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new MfaEnableWebRequest("Str0ngPassw0rd!"))))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store"))
                                .andExpect(jsonPath("$.base32Secret").value("BASE32SECRET"));
        }

        @Test
        void shouldRejectAnonymousEnrollment() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

                mockMvc.perform(post("/auth/mfa/enable")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new MfaEnableWebRequest("Str0ngPassw0rd!"))))
                                .andExpect(status().isUnauthorized());

                verify(enableMfaUseCase, never()).execute(any(), any());
        }

        @Test
        void shouldConfirmEnrollmentForTheCurrentUser() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));

                mockMvc.perform(post("/auth/mfa/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new MfaConfirmWebRequest("Str0ngPassw0rd!", "123456"))))
                                .andExpect(status().isNoContent())
                                .andExpect(header().string("Cache-Control", "no-store"));

                verify(confirmMfaUseCase).execute(7L, "Str0ngPassw0rd!", "123456");
        }

        @Test
        void shouldRejectAnonymousConfirmation() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

                mockMvc.perform(post("/auth/mfa/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new MfaConfirmWebRequest("Str0ngPassw0rd!", "123456"))))
                                .andExpect(status().isUnauthorized());

                verify(confirmMfaUseCase, never()).execute(any(), any(), any());
        }

        @Test
        void shouldRejectAnonymousDisable() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

                mockMvc.perform(post("/auth/mfa/disable")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new MfaDisableWebRequest("Str0ngPassw0rd!", "123456"))))
                                .andExpect(status().isUnauthorized());

                verify(disableMfaUseCase, never()).execute(any(), any(), any());
        }

        @Test
        void shouldDisableMfaForTheCurrentUser() throws Exception {
                when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));

                mockMvc.perform(post("/auth/mfa/disable")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new MfaDisableWebRequest("Str0ngPassw0rd!", "123456"))))
                                .andExpect(status().isNoContent())
                                .andExpect(header().string("Cache-Control", "no-store"));

                verify(disableMfaUseCase).execute(7L, "Str0ngPassw0rd!", "123456");
        }

        @Test
        void shouldAcceptPasswordResetRequest() throws Exception {
                mockMvc.perform(post("/auth/password/reset/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new PasswordResetRequestWebRequest("alice"))))
                                .andExpect(status().isAccepted());

                verify(requestPasswordResetUseCase).execute("alice");
        }

        @Test
        void shouldConfirmPasswordReset() throws Exception {
                mockMvc.perform(post("/auth/password/reset/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new PasswordResetConfirmWebRequest("token-1", "New_passw0rd12"))))
                                .andExpect(status().isNoContent())
                                .andExpect(header().string("Cache-Control", "no-store"));

                verify(confirmPasswordResetUseCase).execute("token-1", "New_passw0rd12");
        }
}
