package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.JwtPort.VerifiedToken;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.RefreshTokenReuseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshSessionUseCase")
@SuppressWarnings("null")
class RefreshSessionUseCaseImplTest {

    @Mock private JwtPort jwtPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private ClockProviderPort clockProvider;
    @Mock private com.bank.app.common.application.port.out.AuditEventPort auditEventPort;

    private RefreshSessionUseCase useCase;

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneId.of("UTC"));
    private static final String REFRESH = "valid-refresh-token";
    private static final String HASH = TokenDigest.sha256Hex(REFRESH);

    @BeforeEach
    void setUp() {
        useCase = new RefreshSessionUseCaseImpl(jwtPort, refreshTokenPort, clockProvider, auditEventPort);
        lenient().when(clockProvider.clock()).thenReturn(FIXED);
    }

    private VerifiedToken verified() {
        return new VerifiedToken("alice", 7L, "ROLE_USER", "jti-1",
                NOW.plusSeconds(3600).toEpochMilli());
    }

    private StoredRefresh stored() {
        return new StoredRefresh(HASH, 7L, "family-1",
                LocalDateTime.now(FIXED).plusDays(7), false, null);
    }

    @Test
    @DisplayName("should rotate into a fresh pair sharing the family")
    void shouldRotateSuccessfully() {
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.of(stored()));
        when(jwtPort.generateRefreshToken(7L, "alice", "ROLE_USER")).thenReturn("new-refresh");
        when(jwtPort.generateToken(7L, "alice", "ROLE_USER")).thenReturn("new-access");
        when(jwtPort.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtPort.getExpirationMs()).thenReturn(900000L);

        AuthResponse response = useCase.execute(REFRESH);

        assertThat(response.token()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.userId()).isEqualTo(7L);
        verify(refreshTokenPort).markRotated(eq(HASH), eq(TokenDigest.sha256Hex("new-refresh")));
        verify(refreshTokenPort).save(eq(TokenDigest.sha256Hex("new-refresh")), eq(7L),
                eq("family-1"), any());
    }

    @Test
    @DisplayName("should reject blank token without touching the store")
    void shouldRejectBlankToken() {
        assertThatThrownBy(() -> useCase.execute("  "))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenPort, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("should reject invalid signature")
    void shouldRejectInvalidSignature() {
        when(jwtPort.verifyAndDecode("bad")).thenReturn(null);

        assertThatThrownBy(() -> useCase.execute("bad"))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenPort, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("should reject access token presented as refresh")
    void shouldRejectAccessToken() {
        when(jwtPort.verifyAndDecode("access-token")).thenReturn(verified());
        when(jwtPort.extractTokenType("access-token")).thenReturn("access");

        assertThatThrownBy(() -> useCase.execute("access-token"))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenPort, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("should reject unknown refresh token")
    void shouldRejectUnknownToken() {
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(REFRESH))
                .isExactlyInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("should reject expired refresh token")
    void shouldRejectExpiredToken() {
        StoredRefresh expired = new StoredRefresh(HASH, 7L, "family-1",
                LocalDateTime.now(FIXED).minusMinutes(1), false, null);
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase.execute(REFRESH))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenPort, never()).markRotated(anyString(), anyString());
    }

    @Test
    @DisplayName("should reject plain-revoked token (logged out)")
    void shouldRejectRevokedToken() {
        StoredRefresh revoked = new StoredRefresh(HASH, 7L, "family-1",
                LocalDateTime.now(FIXED).plusDays(7), true, null);
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> useCase.execute(REFRESH))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenPort, never()).revokeFamily(anyString());
    }

    @Test
    @DisplayName("should revoke family on rotated-token reuse")
    void shouldRevokeFamilyOnReuse() {
        StoredRefresh rotated = new StoredRefresh(HASH, 7L, "family-1",
                LocalDateTime.now(FIXED).plusDays(7), true, "replacement-hash");
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> useCase.execute(REFRESH))
                .isExactlyInstanceOf(RefreshTokenReuseException.class);

        verify(refreshTokenPort).revokeFamily("family-1");
        verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("should publish TOKEN_REVOKED when a reused token kills its family (K11/D4)")
    void shouldPublishTokenRevokedOnReuse() {
        StoredRefresh rotated = new StoredRefresh(HASH, 7L, "family-1",
                LocalDateTime.now(FIXED).plusDays(7), true, "replacement-hash");
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> useCase.execute(REFRESH))
                .isExactlyInstanceOf(RefreshTokenReuseException.class);

        var captor = org.mockito.ArgumentCaptor
                .forClass(com.bank.app.common.domain.event.AuditEvent.class);
        verify(auditEventPort).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("TOKEN_REVOKED");
        assertThat(captor.getValue().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("should reject null expiry without rotating (fail-closed)")
    void shouldRejectNullExpiry() {
        // A stored row without an expiry must never be treated as immortal:
        // fail closed exactly like an already-expired row.
        StoredRefresh noExpiry = new StoredRefresh(HASH, 7L, "family-1", null, false, null);
        when(jwtPort.verifyAndDecode(REFRESH)).thenReturn(verified());
        when(jwtPort.extractTokenType(REFRESH)).thenReturn("refresh");
        when(refreshTokenPort.findByTokenHash(HASH)).thenReturn(Optional.of(noExpiry));

        assertThatThrownBy(() -> useCase.execute(REFRESH))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenPort, never()).markRotated(anyString(), anyString());
        verify(jwtPort, never()).generateToken(anyLong(), anyString(), anyString());
    }
}
