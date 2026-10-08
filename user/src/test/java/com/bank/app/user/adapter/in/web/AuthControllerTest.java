package com.bank.app.user.adapter.in.web;

import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.in.web.dto.RefreshTokenRequest;
import com.bank.app.user.adapter.in.web.dto.RegisterWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * In-module slice for the user web adapter.
 *
 * <p>PIT reported the whole controller as NO_COVERAGE because the MockMvc
 * tests live in the {@code app} module, outside this module's
 * {@code targetTests}. These standalone MockMvc tests kill the return-value
 * and conditional mutants locally.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private RegisterUserUseCase registerUserUseCase;
    @Mock private LoginUserUseCase loginUserUseCase;
    @Mock private ClientIpResolverPort clientIpResolver;
    @Mock private LogoutUseCase logoutUseCase;
    @Mock private RefreshSessionUseCase refreshSessionUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                registerUserUseCase, loginUserUseCase, clientIpResolver,
                logoutUseCase, refreshSessionUseCase)).build();
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
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("rt-1"))))
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
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("rt-9"))))
                .andExpect(status().isNoContent());

        verify(logoutUseCase).execute(eq(null), eq("rt-9"));
    }
}
