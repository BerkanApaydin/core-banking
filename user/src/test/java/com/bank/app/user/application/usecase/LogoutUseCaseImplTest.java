package com.bank.app.user.application.usecase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ClockProviderPort clockProvider;

    @Mock
    private AuditEventPort auditEventPort;

    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        logoutUseCase = new LogoutUseCaseImpl(tokenBlacklistPort, jwtPort, refreshTokenPort,
                clockProvider, auditEventPort);
    }

    private void stubClock() {
        when(clockProvider.clock()).thenReturn(Clock.systemUTC());
    }

    @Test
    @DisplayName("should blacklist token when valid Bearer token is provided")
    void shouldBlacklistTokenWhenBearerTokenIsValid() {
        stubClock();
        String authHeader = "Bearer valid-jwt-token";
        when(tokenBlacklistPort.isBlacklisted("valid-jwt-token")).thenReturn(false);
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);

        logoutUseCase.execute(authHeader);

        verify(jwtPort).getRemainingMs("valid-jwt-token");
        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
    }

    @Test
    @DisplayName("should skip blacklist when token already expired")
    void shouldSkipBlacklistWhenTokenExpired() {
        stubClock();
        String authHeader = "Bearer expired-jwt-token";
        when(tokenBlacklistPort.isBlacklisted("expired-jwt-token")).thenReturn(false);
        when(jwtPort.getRemainingMs("expired-jwt-token")).thenReturn(0L);

        logoutUseCase.execute(authHeader);

        verify(jwtPort).getRemainingMs("expired-jwt-token");
        verify(tokenBlacklistPort, never()).blacklist(anyString(), anyLong());
    }

    @Test
    @DisplayName("M-4: already-revoked token short-circuits to a no-op (still 204)")
    void shouldNoOpWhenTokenAlreadyRevoked() {
        when(tokenBlacklistPort.isBlacklisted("revoked-jwt-token")).thenReturn(true);

        logoutUseCase.execute("Bearer revoked-jwt-token");

        verify(tokenBlacklistPort, never()).blacklist(anyString(), anyLong());
        verifyNoInteractions(jwtPort);
        verifyNoInteractions(auditEventPort);
        verifyNoInteractions(refreshTokenPort);
        verify(clockProvider, never()).clock();
    }

    @Test
    @DisplayName("should do nothing when header does not start with Bearer")
    void shouldDoNothingWhenHeaderIsNotBearer() {
        stubClock();
        String authHeader = "Basic token123";

        logoutUseCase.execute(authHeader);

        verifyNoInteractions(jwtPort);
        verifyNoInteractions(tokenBlacklistPort);
    }

    @Test
    @DisplayName("should do nothing when header is null")
    void shouldDoNothingWhenHeaderIsNull() {
        stubClock();
        logoutUseCase.execute(null);

        verifyNoInteractions(jwtPort);
        verifyNoInteractions(tokenBlacklistPort);
    }

    @Test
    @DisplayName("should revoke refresh session when refresh token is supplied")
    void shouldRevokeRefreshTokenWhenSupplied() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("valid-refresh-token")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("valid-refresh-token"), 7L, "family-1",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer valid-jwt-token", "valid-refresh-token");

        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        verify(refreshTokenPort).revokeFamily("family-1");
        verify(refreshTokenPort, never()).revoke(anyString());
    }

    @Test
    @DisplayName("should revoke only the presented token when it is unknown")
    void shouldFallBackToSingleRevokeForUnknownRefreshToken() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("unknown")))
                .thenReturn(java.util.Optional.empty());

        logoutUseCase.execute("Bearer valid-jwt-token", "unknown");

        verify(refreshTokenPort).revoke(TokenDigest.sha256Hex("unknown"));
        verify(refreshTokenPort, never()).revokeFamily(anyString());
    }

    @Test
    @DisplayName("should ignore blank refresh token")
    void shouldIgnoreBlankRefreshToken() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);

        logoutUseCase.execute("Bearer valid-jwt-token", "  ");

        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        verifyNoInteractions(refreshTokenPort);
    }

    @Test
    @DisplayName("should publish LOGOUT audit with the token identity (K11/D4)")
    void shouldPublishLogoutAudit() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(jwtPort.extractUsername("valid-jwt-token")).thenReturn("alice");

        logoutUseCase.execute("Bearer valid-jwt-token");

        var auditCaptor = ArgumentCaptor
                .forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals("LOGOUT", auditCaptor.getValue().action());
        assertEquals("alice", auditCaptor.getValue().username());
    }

    @Test
    @DisplayName("should attribute unverifiable token to system (best-effort)")
    void shouldAttributeUnverifiableTokenToSystem() {
        stubClock();
        // Kills the EmptyObjectReturn mutant ("" vs "system") on
        // bestEffortUsername: an unverifiable Bearer token must fall back to
        // "system", never to an empty identity or an exception.
        when(jwtPort.getRemainingMs("bogus")).thenReturn(0L);
        when(jwtPort.extractUsername("bogus")).thenThrow(new RuntimeException("bad signature"));

        logoutUseCase.execute("Bearer bogus");

        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals("system", auditCaptor.getValue().username());
    }

    @Test
    @DisplayName("should revoke refresh session for non-Bearer callers")
    void shouldRevokeRefreshForNonBearerCaller() {
        stubClock();
        // Kills the Negate mutant on the refresh-only branch (line 52):
        // a non-Bearer header with a refresh token must still attribute the
        // audit to the refresh identity AND revoke it. A mutant that skips
        // the branch leaves "system" instead of "alice".
        when(jwtPort.extractUsername("refresh-9")).thenReturn("alice");
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("refresh-9")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("refresh-9"), 7L, "family-9",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Basic token123", "refresh-9");

        verify(refreshTokenPort).revokeFamily("family-9");
        verifyNoInteractions(tokenBlacklistPort);
        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals("alice", auditCaptor.getValue().username());
    }

    @Test
    @DisplayName("should attribute anonymous logout to system (best-effort)")
    void shouldAttributeAnonymousLogoutToSystem() {
        stubClock();
        // Kills the EmptyObjectReturn mutant ("" vs "system") on
        // bestEffortUsername: unverifiable/absent tokens must not produce an
        // empty audit identity.
        logoutUseCase.execute(null, null);

        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals("system", auditCaptor.getValue().username());
    }

    @Test
    @DisplayName("should still logout when the audit store is down (K11/D4)")
    void shouldLogoutWhenAuditStoreDown() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        doThrow(new RuntimeException("audit down")).when(auditEventPort)
                .publish(any(AuditEvent.class));

        logoutUseCase.execute("Bearer valid-jwt-token");

        // Revocation happened; the audit gap is only logged.
        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
    }
}
