package com.bank.app.user.application.usecase;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutUseCaseImpl")
class LogoutUseCaseImplTest {

    @Mock
    private TokenBlacklistPort tokenBlacklistPort;

    @Mock
    private JwtPort jwtPort;

    @Mock
    private RefreshTokenPort refreshTokenPort;

    @Mock
    private com.bank.app.common.application.port.out.ClockProviderPort clockProvider;

    @Mock
    private com.bank.app.common.application.port.out.AuditEventPort auditEventPort;

    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        logoutUseCase = new LogoutUseCaseImpl(tokenBlacklistPort, jwtPort, refreshTokenPort,
                clockProvider, auditEventPort);
        lenient().when(clockProvider.clock()).thenReturn(Clock.systemUTC());
    }

    @Test
    @DisplayName("should blacklist token when valid Bearer token is provided")
    void shouldBlacklistTokenWhenBearerTokenIsValid() {
        String authHeader = "Bearer valid-jwt-token";
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);

        logoutUseCase.execute(authHeader);

        verify(jwtPort).getRemainingMs("valid-jwt-token");
        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
    }

    @Test
    @DisplayName("should skip blacklist when token already expired")
    void shouldSkipBlacklistWhenTokenExpired() {
        String authHeader = "Bearer expired-jwt-token";
        when(jwtPort.getRemainingMs("expired-jwt-token")).thenReturn(0L);

        logoutUseCase.execute(authHeader);

        verify(jwtPort).getRemainingMs("expired-jwt-token");
        verifyNoInteractions(tokenBlacklistPort);
    }

    @Test
    @DisplayName("should do nothing when header does not start with Bearer")
    void shouldDoNothingWhenHeaderIsNotBearer() {
        String authHeader = "Basic token123";

        logoutUseCase.execute(authHeader);

        verifyNoInteractions(jwtPort);
        verifyNoInteractions(tokenBlacklistPort);
    }

    @Test
    @DisplayName("should do nothing when header is null")
    void shouldDoNothingWhenHeaderIsNull() {
        logoutUseCase.execute(null);

        verifyNoInteractions(jwtPort);
        verifyNoInteractions(tokenBlacklistPort);
    }

    @Test
    @DisplayName("should revoke refresh session when refresh token is supplied")
    void shouldRevokeRefreshTokenWhenSupplied() {
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);

        logoutUseCase.execute("Bearer valid-jwt-token", "valid-refresh-token");

        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        verify(refreshTokenPort).revoke(
                com.bank.app.common.domain.TokenDigest.sha256Hex("valid-refresh-token"));
    }

    @Test
    @DisplayName("should ignore blank refresh token")
    void shouldIgnoreBlankRefreshToken() {
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);

        logoutUseCase.execute("Bearer valid-jwt-token", "  ");

        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        verifyNoInteractions(refreshTokenPort);
    }

    @Test
    @DisplayName("should publish LOGOUT audit with the token identity (K11/D4)")
    void shouldPublishLogoutAudit() {
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(jwtPort.extractUsername("valid-jwt-token")).thenReturn("alice");

        logoutUseCase.execute("Bearer valid-jwt-token");

        var auditCaptor = org.mockito.ArgumentCaptor
                .forClass(com.bank.app.common.domain.event.AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("LOGOUT", auditCaptor.getValue().action());
        org.junit.jupiter.api.Assertions.assertEquals("alice", auditCaptor.getValue().username());
    }

    @Test
    @DisplayName("should still logout when the audit store is down (K11/D4)")
    void shouldLogoutWhenAuditStoreDown() {
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        doThrow(new RuntimeException("audit down")).when(auditEventPort)
                .publish(any(com.bank.app.common.domain.event.AuditEvent.class));

        logoutUseCase.execute("Bearer valid-jwt-token");

        // Revocation happened; the audit gap is only logged.
        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
    }
}
