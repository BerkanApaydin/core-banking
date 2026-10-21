package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.PasswordResetNotificationPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort.PasswordResetToken;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.EncodedPassword;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.UserTestFactory;
import com.bank.app.user.domain.exception.PasswordResetTokenInvalidException;
import com.bank.app.user.domain.exception.WeakPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password reset use cases")
class PasswordResetUseCaseTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock private LoadUserPort loadUserPort;
    @Mock private PasswordResetTokenPort resetTokenPort;
    @Mock private PasswordResetNotificationPort notificationPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private PasswordEncoderPort passwordEncoderPort;
    @Mock private PasswordPolicy passwordPolicy;
    @Mock private ClockProviderPort clockProvider;
    @Mock private AuditEventPort auditEventPort;

    private RequestPasswordResetUseCaseImpl request;
    private ConfirmPasswordResetUseCaseImpl confirm;

    @BeforeEach
    void setUp() {
        request = new RequestPasswordResetUseCaseImpl(loadUserPort, resetTokenPort, notificationPort,
                15, clockProvider, auditEventPort, new SecureRandom(new byte[]{1, 2, 3}));
        confirm = new ConfirmPasswordResetUseCaseImpl(resetTokenPort, loadUserPort, saveUserPort,
                passwordEncoderPort, passwordPolicy, clockProvider, auditEventPort);
        when(clockProvider.clock()).thenReturn(FIXED);
    }

    @Nested
    @DisplayName("RequestPasswordResetUseCase")
    class Request {

        @Test
        @DisplayName("should issue exactly one live token and notify for a known user")
        void shouldIssueAndNotifyForKnownUser() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findByUsernameForUpdate("alice")).thenReturn(Optional.of(user));

            request.execute("alice");

            verify(resetTokenPort).deleteByUserId(any());
            var issued = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(resetTokenPort).issue(issued.capture());
            assertThat(issued.getValue().tokenHash()).hasSize(64);
            assertThat(issued.getValue().expiresAt())
                    .isEqualTo(LocalDateTime.now(FIXED).plusMinutes(15));
            var token = ArgumentCaptor.forClass(String.class);
            verify(notificationPort).sendPasswordResetToken(eq("alice"), any(), token.capture());
            // Only the digest is stored — the raw token must differ from it.
            assertThat(token.getValue()).isNotEqualTo(issued.getValue().tokenHash());
            assertThat(TokenDigest.sha256Hex(token.getValue())).isEqualTo(issued.getValue().tokenHash());
        }

        @Test
        @DisplayName("should stay silent (but audit) for an unknown user")
        void shouldStaySilentForUnknownUser() {
            when(loadUserPort.findByUsernameForUpdate("ghost")).thenReturn(Optional.empty());

            request.execute("ghost");

            verify(resetTokenPort, never()).issue(any());
            verify(notificationPort, never()).sendPasswordResetToken(any(), any(), any());
            verify(auditEventPort).publish(any());
        }
    }

    @Nested
    @DisplayName("ConfirmPasswordResetUseCase")
    class Confirm {

        private static final String RAW_TOKEN = "reset-token-value";

        private PasswordResetToken liveToken() {
            return new PasswordResetToken(TokenDigest.sha256Hex(RAW_TOKEN), 7L,
                    LocalDateTime.now(FIXED).plusMinutes(15), null);
        }

        @Test
        @DisplayName("should consume the token, change the password and retire sessions")
        void shouldResetPassword() {
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            User user = UserTestFactory.newUser("alice");
            long versionBefore = user.getTokenVersion();
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(passwordPolicy.validate("NewStr0ngPass!")).thenReturn(List.of());
            when(passwordEncoderPort.encode("NewStr0ngPass!"))
                    .thenReturn("$2a$12$newhash000000000000000000000000000001");
            when(resetTokenPort.tryMarkUsed(any(), any())).thenReturn(true);

            confirm.execute(RAW_TOKEN, "NewStr0ngPass!");

            verify(resetTokenPort).tryMarkUsed(any(), any());
            verify(saveUserPort).save(user);
            assertThat(user.getTokenVersion()).isEqualTo(versionBefore + 1);
            verify(resetTokenPort).deleteByUserId(7L);
        }

        @Test
        @DisplayName("should reject unknown, expired and used tokens identically")
        void shouldRejectUnusableTokens() {
            PasswordResetToken expired = new PasswordResetToken("h1", 7L,
                    LocalDateTime.now(FIXED).minusMinutes(1), null);
            PasswordResetToken used = new PasswordResetToken("h2", 7L,
                    LocalDateTime.now(FIXED).plusMinutes(15), LocalDateTime.now(FIXED));

            when(resetTokenPort.findByHash(TokenDigest.sha256Hex("unknown"))).thenReturn(Optional.empty());
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex("expired")))
                    .thenReturn(Optional.of(expired));
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex("used")))
                    .thenReturn(Optional.of(used));

            assertThatThrownBy(() -> confirm.execute("unknown", "NewStr0ngPass!"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
            assertThatThrownBy(() -> confirm.execute("expired", "NewStr0ngPass!"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
            assertThatThrownBy(() -> confirm.execute("used", "NewStr0ngPass!"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject a weak new password without consuming a valid token path")
        void shouldRejectWeakPassword() {
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            User user = UserTestFactory.newUser("alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(passwordPolicy.validate("weak")).thenReturn(List.of("too short"));

            assertThatThrownBy(() -> confirm.execute(RAW_TOKEN, "weak"))
                    .isInstanceOf(WeakPasswordException.class);
            verify(saveUserPort, never()).save(any());
            verify(resetTokenPort, never()).tryMarkUsed(any(), any());
        }

        @Test
        @DisplayName("should fail generic when losing the consumption race")
        void shouldFailWhenLosingRace() {
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            User user = UserTestFactory.newUser("alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(passwordPolicy.validate("NewStr0ngPass!")).thenReturn(List.of());
            when(resetTokenPort.tryMarkUsed(any(), any())).thenReturn(false);

            assertThatThrownBy(() -> confirm.execute(RAW_TOKEN, "NewStr0ngPass!"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
            verify(saveUserPort, never()).save(any());
        }
    }

    private static User persistedUser(Long id, String username) {
        return new User(new UserId(id), username,
                EncodedPassword.of(UserTestFactory.FIXTURE_HASH),
                Role.ROLE_USER, null, null, 1L, 0L);
    }
}
