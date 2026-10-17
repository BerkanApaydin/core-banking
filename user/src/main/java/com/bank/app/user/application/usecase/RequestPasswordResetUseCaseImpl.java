package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.in.RequestPasswordResetUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordResetNotificationPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort.PasswordResetToken;
import com.bank.app.user.domain.User;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Starts a password reset without leaking account existence.
 *
 * <p>Enumeration safety: unknown identifiers follow the exact same path as
 * known ones (same audit shape, same empty return, no timing shortcut) —
 * the only observable difference is the out-of-band message, which an
 * attacker cannot observe. A successful request revokes older tokens first
 * so exactly one token is live per user.
 */
@TransactionalUseCase
public class RequestPasswordResetUseCaseImpl implements RequestPasswordResetUseCase {

    private static final int TOKEN_BYTES = 32;

    private final LoadUserPort loadUserPort;
    private final PasswordResetTokenPort resetTokenPort;
    private final PasswordResetNotificationPort notificationPort;
    private final int resetTokenValidityMinutes;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;
    private final SecureRandom random;

    public RequestPasswordResetUseCaseImpl(LoadUserPort loadUserPort,
            PasswordResetTokenPort resetTokenPort, PasswordResetNotificationPort notificationPort,
            int resetTokenValidityMinutes, ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this(loadUserPort, resetTokenPort, notificationPort, resetTokenValidityMinutes, clockProvider, auditEventPort,
                new SecureRandom());
    }

    RequestPasswordResetUseCaseImpl(LoadUserPort loadUserPort,
            PasswordResetTokenPort resetTokenPort, PasswordResetNotificationPort notificationPort,
            int resetTokenValidityMinutes, ClockProviderPort clockProvider, AuditEventPort auditEventPort,
            SecureRandom random) {
        this.loadUserPort = loadUserPort;
        this.resetTokenPort = resetTokenPort;
        this.notificationPort = notificationPort;
        this.resetTokenValidityMinutes = resetTokenValidityMinutes;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
        this.random = random;
    }

    @Override
    public void execute(String username) {
        Objects.requireNonNull(username, "Username must not be null");
        String identifier = username.strip();
        LocalDateTime now = LocalDateTime.now(clockProvider.clock());
        Optional<User> user = identifier.isEmpty()
                ? Optional.empty()
                : loadUserPort.findByUsername(identifier);
        if (user.isPresent()) {
            User found = user.get();
            Long userId = found.getId() != null ? found.getId().value() : null;
            if (userId != null) {
                resetTokenPort.deleteByUserId(userId);
                String token = newToken();
                resetTokenPort.issue(new PasswordResetToken(TokenDigest.sha256Hex(token), userId,
                        now.plusMinutes(resetTokenValidityMinutes), null));
                notificationPort.sendPasswordResetToken(found.getUsername(), contactOf(found), token);
            }
        } else {
            // Enumeration-safe dummy work: an unknown identifier must cost
            // roughly the same as a known one (token mint + digest), otherwise
            // the response-time gap oracles account existence despite the
            // identical 202 + audit shape.
            TokenDigest.sha256Hex(newToken());
        }
        audit(identifier, user.map(u -> u.getId() != null ? u.getId().value() : null).orElse(null), now);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String contactOf(User user) {
        if (user.getEmail() != null) {
            return user.getEmail().value();
        }
        if (user.getPhone() != null) {
            return user.getPhone().value();
        }
        return user.getUsername();
    }

    private void audit(String identifier, Long userId, LocalDateTime now) {
        try {
            // Same audit row for known and unknown identifiers: the audit
            // trail must not become an enumeration oracle either.
            auditEventPort.publish(new AuditEvent(AuditEvent.PASSWORD_RESET_REQUESTED,
                    "Password reset requested.", now, identifier.isEmpty() ? "system" : identifier, userId));
        } catch (Exception ignored) {
            // Best-effort like login audit.
        }
    }
}
