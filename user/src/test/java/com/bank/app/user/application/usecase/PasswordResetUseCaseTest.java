package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.PasswordResetNotificationPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort.PasswordResetToken;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.EncodedPassword;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.PhoneNumber;
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

import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private TransactionBoundaryPort transactionBoundary;

    private RequestPasswordResetUseCaseImpl request;
    private ConfirmPasswordResetUseCaseImpl confirm;

    @BeforeEach
    void setUp() {
        request = new RequestPasswordResetUseCaseImpl(loadUserPort, resetTokenPort, notificationPort,
                15, clockProvider, auditEventPort, transactionBoundary, new SecureRandom(new byte[]{1, 2, 3}));
        confirm = new ConfirmPasswordResetUseCaseImpl(resetTokenPort, loadUserPort, saveUserPort,
                passwordEncoderPort, passwordPolicy, clockProvider, auditEventPort);
        when(clockProvider.clock()).thenReturn(FIXED);
    }

    @Nested
    @DisplayName("RequestPasswordResetUseCase")
    class Request {

        @BeforeEach
        void runAfterCommitImmediately() {
            lenient().doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(transactionBoundary).runAfterCommit(any(Runnable.class));
        }

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
            verify(notificationPort).sendPasswordResetToken(eq("alice"), eq("alice"), token.capture());
            // Only the digest is stored — the raw token must differ from it.
            assertThat(token.getValue()).hasSize(43);
            assertThat(token.getValue()).isNotEqualTo(issued.getValue().tokenHash());
            assertThat(TokenDigest.sha256Hex(token.getValue())).isEqualTo(issued.getValue().tokenHash());
            var audit = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventPort).publishBestEffort(audit.capture());
            assertThat(audit.getValue().action()).isEqualTo(AuditEvent.PASSWORD_RESET_REQUESTED);
            assertThat(audit.getValue().username()).isEqualTo("alice");
            assertThat(audit.getValue().actorUserId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("should prefer the email contact and fall back to the phone")
        void shouldChooseNotificationContact() {
            User withEmail = User.rehydrate(new UserId(7L), "alice",
                    EncodedPassword.of(UserTestFactory.FIXTURE_HASH), Role.ROLE_USER,
                    new EmailAddress("alice@example.com"), new PhoneNumber("555-0100"), 1L, 0L);
            User withPhone = User.rehydrate(new UserId(8L), "bob",
                    EncodedPassword.of(UserTestFactory.FIXTURE_HASH), Role.ROLE_USER,
                    null, new PhoneNumber("555-0101"), 1L, 0L);
            when(loadUserPort.findByUsernameForUpdate("alice")).thenReturn(Optional.of(withEmail));
            when(loadUserPort.findByUsernameForUpdate("bob")).thenReturn(Optional.of(withPhone));

            request.execute("alice");
            request.execute("bob");

            verify(notificationPort).sendPasswordResetToken(eq("alice"), eq("alice@example.com"), any());
            verify(notificationPort).sendPasswordResetToken(eq("bob"), eq("555-0101"), any());
        }

        @Test
        @DisplayName("should fall back to the username when neither email nor phone is on file")
        void shouldFallBackToTheUsernameContact() {
            // Third fallback: a user without any contact channel still has to
            // receive the reset token, otherwise the account is unrecoverable.
            User contactless = User.rehydrate(new UserId(9L), "alice.ops",
                    EncodedPassword.of(UserTestFactory.FIXTURE_HASH), Role.ROLE_USER,
                    null, null, 1L, 0L);
            when(loadUserPort.findByUsernameForUpdate("alice.ops"))
                    .thenReturn(Optional.of(contactless));

            request.execute("alice.ops");

            verify(notificationPort).sendPasswordResetToken(eq("alice.ops"), eq("alice.ops"), any());
        }

        @Test
        @DisplayName("should look up the trimmed identifier")
        void shouldLookUpTheTrimmedIdentifier() {
            // A pasted identifier must reach the store stripped, otherwise the
            // lookup misses and the user never receives a reset link.
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findByUsernameForUpdate("alice")).thenReturn(Optional.of(user));

            request.execute("  alice  ");

            verify(loadUserPort).findByUsernameForUpdate("alice");
            verify(loadUserPort, never()).findByUsernameForUpdate("  alice  ");
            verify(notificationPort).sendPasswordResetToken(eq("alice"), any(), any());
        }

        @Test
        @DisplayName("should mint a fresh token on every request")
        void shouldMintFreshTokenPerRequest() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findByUsernameForUpdate("alice")).thenReturn(Optional.of(user));
            var tokens = ArgumentCaptor.forClass(String.class);

            request.execute("alice");
            request.execute("alice");

            verify(notificationPort, times(2))
                    .sendPasswordResetToken(eq("alice"), eq("alice"), tokens.capture());
            assertThat(tokens.getAllValues()).hasSize(2).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("should defer the notification until the transaction commits")
        void shouldDeferNotificationUntilCommit() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findByUsernameForUpdate("alice")).thenReturn(Optional.of(user));
            doNothing().when(transactionBoundary).runAfterCommit(any());

            request.execute("alice");

            verify(notificationPort, never()).sendPasswordResetToken(any(), any(), any());
            var deferred = ArgumentCaptor.forClass(Runnable.class);
            verify(transactionBoundary).runAfterCommit(deferred.capture());
            deferred.getValue().run();
            verify(notificationPort).sendPasswordResetToken(eq("alice"), any(), any());
        }

        @Test
        @DisplayName("should stay silent (but audit) for an unknown user")
        void shouldStaySilentForUnknownUser() {
            when(loadUserPort.findByUsernameForUpdate("ghost")).thenReturn(Optional.empty());

            request.execute("ghost");

            verify(resetTokenPort, never()).issue(any());
            verify(notificationPort, never()).sendPasswordResetToken(any(), any(), any());
            verify(auditEventPort).publishBestEffort(any());
        }

        @Test
        @DisplayName("should not query the store for a blank identifier")
        void shouldSkipStoreForBlankIdentifier() {
            request.execute("   ");

            verifyNoInteractions(loadUserPort);
            verifyNoInteractions(resetTokenPort);
            verifyNoInteractions(notificationPort);
            verify(auditEventPort).publishBestEffort(any());
        }

        @Test
        @DisplayName("should not issue a token for a user without a persisted id")
        void shouldNotIssueForTransientUser() {
            User transientUser = User.rehydrate(null, "alice",
                    EncodedPassword.of(UserTestFactory.FIXTURE_HASH), Role.ROLE_USER,
                    null, null, null, 0L);
            when(loadUserPort.findByUsernameForUpdate("alice"))
                    .thenReturn(Optional.of(transientUser));

            request.execute("alice");

            verifyNoInteractions(resetTokenPort);
            verifyNoInteractions(notificationPort);
            verify(auditEventPort).publishBestEffort(any());
        }

        @Test
        @DisplayName("should swallow a post-commit notification failure")
        void shouldSwallowNotificationFailure() {
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findByUsernameForUpdate("alice")).thenReturn(Optional.of(user));
            doThrow(new IllegalStateException("mail down"))
                    .when(notificationPort).sendPasswordResetToken(any(), any(), any());

            request.execute("alice");

            verify(resetTokenPort).issue(any());
            verify(auditEventPort).publishBestEffort(any());
        }

        @Test
        @DisplayName("should swallow an audit-store failure")
        void shouldSwallowAuditFailure() {
            when(loadUserPort.findByUsernameForUpdate("ghost")).thenReturn(Optional.empty());
            doThrow(new IllegalStateException("audit down"))
                    .when(auditEventPort).publishBestEffort(any());

            request.execute("ghost");

            verify(notificationPort, never()).sendPasswordResetToken(any(), any(), any());
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
            User user = persistedUser(7L, "alice");
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
            var audit = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventPort).publishBestEffort(audit.capture());
            assertThat(audit.getValue().action()).isEqualTo(AuditEvent.PASSWORD_RESET_COMPLETED);
            assertThat(audit.getValue().username()).isEqualTo("alice");
            assertThat(audit.getValue().actorUserId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("should reject an expired token even when every downstream step would succeed")
        void shouldRejectExpiredTokenBeforeDownstreamWork() {
            PasswordResetToken expired = new PasswordResetToken(
                    TokenDigest.sha256Hex("expired"), 7L,
                    LocalDateTime.now(FIXED).minusMinutes(1), null);
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex("expired")))
                    .thenReturn(Optional.of(expired));
            lenient().when(loadUserPort.findById(7L))
                    .thenReturn(Optional.of(persistedUser(7L, "alice")));
            lenient().when(passwordPolicy.validate("NewStr0ngPass!")).thenReturn(List.of());
            lenient().when(resetTokenPort.tryMarkUsed(any(), any())).thenReturn(true);
            lenient().when(passwordEncoderPort.encode("NewStr0ngPass!"))
                    .thenReturn("$2a$12$newhash000000000000000000000000000001");

            assertThatThrownBy(() -> confirm.execute("expired", "NewStr0ngPass!"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
            verify(saveUserPort, never()).save(any());
            verify(resetTokenPort, never()).tryMarkUsed(any(), any());
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
        @DisplayName("should reject a token expiring exactly at the current instant")
        void shouldRejectExpiryBoundaryAtNow() {
            PasswordResetToken boundary = new PasswordResetToken(
                    TokenDigest.sha256Hex(RAW_TOKEN), 7L,
                    LocalDateTime.now(FIXED), null);
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(boundary));

            assertThatThrownBy(() -> confirm.execute(RAW_TOKEN, "NewStr0ngPass!"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);

            verify(resetTokenPort, never()).tryMarkUsed(any(), any());
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

        @Test
        @DisplayName("should reject a valid token whose user no longer exists like an invalid token")
        void shouldRejectAValidTokenOfADeletedUser() {
            // The token is valid, unexpired and unused, but the user row is
            // gone. A distinguishable error would turn the public confirm
            // endpoint into an account-existence oracle.
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            when(loadUserPort.findById(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> confirm.execute(RAW_TOKEN, "NewStr0ngPass!"))
                    .isExactlyInstanceOf(PasswordResetTokenInvalidException.class);

            verify(resetTokenPort, never()).tryMarkUsed(any(), any());
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject a null new password as a weak password, not as a server error")
        void shouldRejectANullNewPassword() {
            // An omitted password must surface as a business failure the
            // client can act on, never as a 500.
            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(persistedUser(7L, "alice")));

            assertThatThrownBy(() -> withRealPolicy().execute(RAW_TOKEN, null))
                    .isInstanceOf(WeakPasswordException.class);

            verify(resetTokenPort, never()).tryMarkUsed(any(), any());
            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should accept a password exactly at the policy minimum")
        void shouldAcceptAPasswordAtThePolicyMinimum() {
            // The boundary must not be off by one: 12 characters with an
            // upper, a lower and a digit is a legal password.
            String atMinimum = "Banksecure1A";
            assertThat(atMinimum).hasSize(12);

            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            User user = persistedUser(7L, "alice");
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoderPort.encode(atMinimum))
                    .thenReturn("$2a$12$newhash000000000000000000000000000001");
            when(resetTokenPort.tryMarkUsed(any(), any())).thenReturn(true);

            withRealPolicy().execute(RAW_TOKEN, atMinimum);

            verify(saveUserPort).save(user);
            verify(resetTokenPort).tryMarkUsed(any(), any());
        }

        @Test
        @DisplayName("should reject a password one character below the policy minimum")
        void shouldRejectAPasswordBelowThePolicyMinimum() {
            String belowMinimum = "Banksecur1A";
            assertThat(belowMinimum).hasSize(11);

            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(persistedUser(7L, "alice")));

            assertThatThrownBy(() -> withRealPolicy().execute(RAW_TOKEN, belowMinimum))
                    .isInstanceOf(WeakPasswordException.class);

            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject a password longer than the BCrypt byte budget")
        void shouldRejectAPasswordOverTheByteBudget() {
            // BCrypt truncates at 72 bytes: two different 73-character
            // passwords would hash identically.
            String tooLong = "Aa1" + "x".repeat(70);
            assertThat(tooLong).hasSize(73);

            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(persistedUser(7L, "alice")));

            assertThatThrownBy(() -> withRealPolicy().execute(RAW_TOKEN, tooLong))
                    .isInstanceOf(WeakPasswordException.class);

            verify(saveUserPort, never()).save(any());
        }

        @Test
        @DisplayName("should reject a multi-byte password that fits in 72 characters but not in 72 bytes")
        void shouldRejectAMultiBytePasswordOverTheByteBudget() {
            // 30 characters of "€" are 90 UTF-8 bytes: the character count
            // looks legal, the byte budget does not.
            String multiByte = "€".repeat(30);
            assertThat(multiByte).hasSize(30);
            assertThat(multiByte.getBytes(StandardCharsets.UTF_8)).hasSize(90);

            when(resetTokenPort.findByHash(TokenDigest.sha256Hex(RAW_TOKEN)))
                    .thenReturn(Optional.of(liveToken()));
            when(loadUserPort.findById(7L)).thenReturn(Optional.of(persistedUser(7L, "alice")));

            assertThatThrownBy(() -> withRealPolicy().execute(RAW_TOKEN, multiByte))
                    .isInstanceOf(WeakPasswordException.class);

            verify(saveUserPort, never()).save(any());
        }

        /** A confirm use case wired to the real policy instead of a mock. */
        private ConfirmPasswordResetUseCaseImpl withRealPolicy() {
            return new ConfirmPasswordResetUseCaseImpl(resetTokenPort, loadUserPort, saveUserPort,
                    passwordEncoderPort, PasswordPolicy.DEFAULT, clockProvider, auditEventPort);
        }
    }

    private static User persistedUser(Long id, String username) {
        return User.rehydrate(new UserId(id), username,
                EncodedPassword.of(UserTestFactory.FIXTURE_HASH),
                Role.ROLE_USER, null, null, 1L, 0L);
    }
}
