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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(clockProvider.clock()).thenReturn(
                java.time.Clock.fixed(java.time.Instant.parse("2026-06-22T10:00:00Z"),
                        java.time.ZoneOffset.UTC));
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
        when(jwtPort.verifyAndDecode("valid-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("valid-refresh-token")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("valid-refresh-token"), 7L, "family-1",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer valid-jwt-token", "valid-refresh-token");

        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        verify(refreshTokenPort).revokeFamily("family-1", 7L);
        verify(refreshTokenPort, never()).revoke(anyString());
    }

    @Test
    @DisplayName("R-U11: revoke the refresh family before blacklisting the access token")
    void shouldRevokeRefreshFamilyBeforeBlacklistingAccessToken() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(jwtPort.verifyAndDecode("valid-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("valid-refresh-token")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("valid-refresh-token"), 7L, "family-1",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer valid-jwt-token", "valid-refresh-token");

        InOrder order = inOrder(refreshTokenPort, tokenBlacklistPort);
        order.verify(refreshTokenPort).revokeFamily("family-1", 7L);
        order.verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
    }

    @Test
    @DisplayName("R-U11: a failed refresh revocation must not burn the access token")
    void shouldNotBlacklistAccessTokenWhenRefreshRevocationFails() {
        when(jwtPort.verifyAndDecode("valid-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("valid-refresh-token")))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> logoutUseCase.execute(
                "Bearer valid-jwt-token", "valid-refresh-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db down");

        verify(jwtPort, never()).getRemainingMs(anyString());
        verify(tokenBlacklistPort, never()).blacklist(anyString(), anyLong());
    }

    @Test
    @DisplayName("an unverified access token with an unknown refresh token revokes only the presented row")
    void shouldRevokeOnlyPresentedRowForUnverifiedAccessAndUnknownRefresh() {
        stubClock();
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);

        logoutUseCase.execute("Bearer valid-jwt-token", "unknown");

        verify(refreshTokenPort).revoke(TokenDigest.sha256Hex("unknown"));
        verify(refreshTokenPort, never()).revokeFamily(anyString(), anyLong());
    }

    @Test
    @DisplayName("an unverifiable access token revokes only the presented refresh row")
    void shouldRevokeOnlyPresentedRefreshRowWhenAccessTokenCannotBeVerified() {
        stubClock();
        when(tokenBlacklistPort.isBlacklisted("expired-jwt-token")).thenReturn(false);
        when(jwtPort.getRemainingMs("expired-jwt-token")).thenReturn(0L);

        logoutUseCase.execute("Bearer expired-jwt-token", "valid-refresh-token");

        verify(refreshTokenPort).revoke(TokenDigest.sha256Hex("valid-refresh-token"));
        verify(refreshTokenPort, never()).findByTokenHash(anyString());
        verify(refreshTokenPort, never()).revokeFamily(anyString(), anyLong());
    }

    @Test
    @DisplayName("an already-revoked access token still revokes the presented refresh family")
    void shouldRevokeFamilyForAlreadyRevokedAccessToken() {
        when(tokenBlacklistPort.isBlacklisted("revoked-jwt-token")).thenReturn(true);
        when(jwtPort.verifyAndDecode("revoked-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("refresh-9")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("refresh-9"), 7L, "family-9",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer revoked-jwt-token", "refresh-9");

        verify(refreshTokenPort).revokeFamily("family-9", 7L);
        verify(tokenBlacklistPort, never()).blacklist(anyString(), anyLong());
        verifyNoInteractions(auditEventPort);
    }

    @Test
    @DisplayName("an already-revoked, expired access token revokes only the presented refresh row")
    void shouldRevokeOnlyPresentedRefreshRowForExpiredRevokedAccessToken() {
        when(tokenBlacklistPort.isBlacklisted("revoked-jwt-token")).thenReturn(true);

        logoutUseCase.execute("Bearer revoked-jwt-token", "refresh-9");

        verify(refreshTokenPort).revoke(TokenDigest.sha256Hex("refresh-9"));
        verify(refreshTokenPort, never()).findByTokenHash(anyString());
        verify(refreshTokenPort, never()).revokeFamily(anyString(), anyLong());
        verify(tokenBlacklistPort, never()).blacklist(anyString(), anyLong());
        verifyNoInteractions(auditEventPort);
    }

    @Test
    @DisplayName("an unreadable revocation store falls through to a normal logout")
    void shouldContinueWhenRevocationCheckFails() {
        stubClock();
        when(tokenBlacklistPort.isBlacklisted("valid-jwt-token"))
                .thenThrow(new RuntimeException("redis down"));
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(jwtPort.extractUsername("valid-jwt-token")).thenReturn("alice");

        logoutUseCase.execute("Bearer valid-jwt-token");

        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals("alice", auditCaptor.getValue().username());
    }

    @Test
    @DisplayName("best-effort attribution falls back to system for null and blank tokens")
    void shouldFallBackToSystemForNullAndBlankTokens() throws Exception {
        Method bestEffort = LogoutUseCaseImpl.class.getDeclaredMethod(
                "bestEffortUsername", String[].class);
        bestEffort.setAccessible(true);

        Object username = bestEffort.invoke(logoutUseCase, (Object) new String[]{null, " "});

        assertEquals("system", username);
        verifyNoInteractions(jwtPort);
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

        verify(refreshTokenPort).revokeFamily("family-9", 7L);
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

    @Test
    @DisplayName("binds the refresh family to the verified access-token subject")
    void shouldBindRefreshFamilyToAccessTokenSubject() {
        stubClock();
        when(tokenBlacklistPort.isBlacklisted("valid-jwt-token")).thenReturn(false);
        when(jwtPort.verifyAndDecode("valid-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("valid-refresh-token")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("valid-refresh-token"), 7L, "family-1",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer valid-jwt-token", "valid-refresh-token");

        verify(refreshTokenPort).revokeFamily("family-1", 7L);
        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals(7L, auditCaptor.getValue().actorUserId());
    }

    @Test
    @DisplayName("does not revoke a refresh family owned by another user")
    void shouldNotRevokeRefreshFamilyOwnedByAnotherUser() {
        stubClock();
        when(tokenBlacklistPort.isBlacklisted("valid-jwt-token")).thenReturn(false);
        when(jwtPort.verifyAndDecode("valid-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(jwtPort.getRemainingMs("valid-jwt-token")).thenReturn(3600000L);
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("victim-refresh")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("victim-refresh"), 9L, "family-victim",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer valid-jwt-token", "victim-refresh");

        verify(refreshTokenPort, never()).revokeFamily(anyString(), anyLong());
        verify(refreshTokenPort, never()).revoke(anyString());
        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPort).publish(auditCaptor.capture());
        assertEquals(7L, auditCaptor.getValue().actorUserId());
    }

    @Test
    @DisplayName("an already-revoked access token cannot revoke another user's refresh family")
    void shouldNotRevokeAnotherUsersFamilyForAlreadyRevokedAccessToken() {
        when(tokenBlacklistPort.isBlacklisted("revoked-jwt-token")).thenReturn(true);
        when(jwtPort.verifyAndDecode("revoked-jwt-token")).thenReturn(new JwtPort.VerifiedToken(
                "alice", 7L, "USER", "jti-1", 0L));
        when(refreshTokenPort.findByTokenHash(TokenDigest.sha256Hex("victim-refresh")))
                .thenReturn(java.util.Optional.of(new StoredRefresh(
                        TokenDigest.sha256Hex("victim-refresh"), 9L, "family-victim",
                        java.time.LocalDateTime.now().plusDays(1), false, null)));

        logoutUseCase.execute("Bearer revoked-jwt-token", "victim-refresh");

        verify(refreshTokenPort, never()).revokeFamily(anyString(), anyLong());
        verify(refreshTokenPort, never()).revoke(anyString());
        verify(tokenBlacklistPort, never()).blacklist(anyString(), anyLong());
        verifyNoInteractions(auditEventPort);
    }
}
