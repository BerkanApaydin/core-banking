package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.TransactionBoundaryPort;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a password reset without leaking account existence.
 *
 * <p>Enumeration safety: known and unknown identifiers produce the same
 * external response and audit shape. The unknown path performs best-effort
 * dummy digest work so it is not free to probe, but timing parity is NOT
 * claimed: the known path also deletes/inserts a token under a pessimistic
 * lock, so the two paths can still differ measurably. The out-of-band
 * notification is dispatched only after commit, so a failed transaction never
 * sends a token and provider latency never extends the row-lock window. The
 * only intended observable difference is the out-of-band message, which an
 * attacker cannot observe. A successful request revokes older tokens first so
 * exactly one token is live per user.
 * R-U12: the delete+insert pair runs under a pessimistic lock on the user
 * row (plus a partial unique index in V58), so concurrent requests cannot
 * leave two live tokens behind.
 */
@TransactionalUseCase
public class RequestPasswordResetUseCaseImpl implements RequestPasswordResetUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetUseCaseImpl.class);

    private static final int TOKEN_BYTES = 32;

    private final LoadUserPort loadUserPort;
    private final PasswordResetTokenPort resetTokenPort;
    private final PasswordResetNotificationPort notificationPort;
    private final int resetTokenValidityMinutes;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;
    private final TransactionBoundaryPort transactionBoundary;
    private final SecureRandom random;

    public RequestPasswordResetUseCaseImpl(LoadUserPort loadUserPort,
            PasswordResetTokenPort resetTokenPort, PasswordResetNotificationPort notificationPort,
            int resetTokenValidityMinutes, ClockProviderPort clockProvider, AuditEventPort auditEventPort,
            TransactionBoundaryPort transactionBoundary) {
        this(loadUserPort, resetTokenPort, notificationPort, resetTokenValidityMinutes, clockProvider, auditEventPort,
                transactionBoundary, new SecureRandom());
    }

    RequestPasswordResetUseCaseImpl(LoadUserPort loadUserPort,
            PasswordResetTokenPort resetTokenPort, PasswordResetNotificationPort notificationPort,
            int resetTokenValidityMinutes, ClockProviderPort clockProvider, AuditEventPort auditEventPort,
            TransactionBoundaryPort transactionBoundary, SecureRandom random) {
        this.loadUserPort = loadUserPort;
        this.resetTokenPort = resetTokenPort;
        this.notificationPort = notificationPort;
        this.resetTokenValidityMinutes = resetTokenValidityMinutes;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
        this.transactionBoundary = transactionBoundary;
        this.random = random;
    }

    @Override
    public void execute(String username) {
        Objects.requireNonNull(username, "Username must not be null");
        String identifier = username.strip();
        LocalDateTime now = LocalDateTime.now(clockProvider.clock());
        Optional<User> user = identifier.isEmpty()
                ? Optional.empty()
                : loadUserPort.findByUsernameForUpdate(identifier);
        if (user.isPresent()) {
            User found = user.get();
            Long userId = found.getId() != null ? found.getId().value() : null;
            if (userId != null) {
                resetTokenPort.deleteByUserId(userId);
                String token = newToken();
                resetTokenPort.issue(new PasswordResetToken(TokenDigest.sha256Hex(token), userId,
                        now.plusMinutes(resetTokenValidityMinutes), null));
                notifyAfterCommit(found, token);
            }
        } else {
            // Best-effort dummy work: an unknown identifier still mints a
            // token and burns a digest so it is not free to probe. This does
            // NOT equalize timing with the known path (which also locks,
            // persists and notifies); the identical response/audit shape is
            // the enumeration guarantee, not timing.
            TokenDigest.sha256Hex(newToken());
        }
        audit(identifier, user.map(u -> u.getId() != null ? u.getId().value() : null).orElse(null), now);
    }

    private void notifyAfterCommit(User user, String token) {
        String username = user.getUsername();
        String contact = contactOf(user);
        transactionBoundary.runAfterCommit(() -> {
            try {
                notificationPort.sendPasswordResetToken(username, contact, token);
            } catch (RuntimeException notificationFailure) {
                // The token is already committed; surfacing a 500 would invite
                // a retry that rotates it. Log without the token/contact and
                // let the caller keep the enumeration-neutral 202 response.
                log.warn("Password-reset notification failed after commit: {}",
                        notificationFailure.getClass().getSimpleName());
            }
        });
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
            auditEventPort.publishBestEffort(new AuditEvent(AuditEvent.PASSWORD_RESET_REQUESTED,
                    "Password reset requested.", now, identifier.isEmpty() ? "system" : identifier, userId));
        } catch (Exception ignored) {
            // Best-effort like login audit.
        }
    }
}
