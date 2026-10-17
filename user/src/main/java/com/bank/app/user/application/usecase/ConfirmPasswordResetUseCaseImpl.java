package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.in.ConfirmPasswordResetUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort.PasswordResetToken;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.EncodedPassword;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.PasswordResetTokenInvalidException;
import com.bank.app.user.domain.exception.WeakPasswordException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Completes a password reset: consumes the single-use token and sets the new
 * password. The token is consumed BEFORE the password write is attempted, so
 * a concurrent confirm with the same token cannot double-apply (the loser
 * sees a used token and fails with the same generic message).
 */
@TransactionalUseCase
public class ConfirmPasswordResetUseCaseImpl implements ConfirmPasswordResetUseCase {

    private final PasswordResetTokenPort resetTokenPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final PasswordPolicy passwordPolicy;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public ConfirmPasswordResetUseCaseImpl(PasswordResetTokenPort resetTokenPort,
            LoadUserPort loadUserPort, SaveUserPort saveUserPort,
            PasswordEncoderPort passwordEncoderPort, PasswordPolicy passwordPolicy,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.resetTokenPort = resetTokenPort;
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.passwordPolicy = passwordPolicy;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public void execute(String token, String newPassword) {
        Objects.requireNonNull(token, "Token must not be null");
        LocalDateTime now = LocalDateTime.now(clockProvider.clock());
        PasswordResetToken reset = resetTokenPort.findByHash(TokenDigest.sha256Hex(token.strip()))
                .filter(t -> t.isUsableAt(now))
                .orElseThrow(PasswordResetTokenInvalidException::new);
        // A valid token for a deleted user degrades to the same generic
        // message: the confirm endpoint must not oracle account existence.
        User user = loadUserPort.findById(reset.userId())
                .orElseThrow(PasswordResetTokenInvalidException::new);
        List<String> violations = passwordPolicy.validate(newPassword);
        if (!violations.isEmpty()) {
            throw new WeakPasswordException(violations);
        }
        // Consume before writing (conditional UPDATE): a concurrent confirm
        // with the same token loses here and fails generic instead of
        // double-applying.
        if (!resetTokenPort.tryMarkUsed(reset.tokenHash(), now)) {
            throw new PasswordResetTokenInvalidException();
        }
        // changePassword bumps tokenVersion: every outstanding session dies at
        // its next refresh — a reset must never leave stolen sessions alive.
        user.changePassword(EncodedPassword.of(passwordEncoderPort.encode(newPassword)));
        saveUserPort.save(user);
        resetTokenPort.deleteByUserId(reset.userId());
        audit(user, now);
    }

    private void audit(User user, LocalDateTime now) {
        try {
            auditEventPort.publish(new AuditEvent(AuditEvent.PASSWORD_RESET_COMPLETED,
                    "Password reset completed; outstanding sessions retired.", now,
                    user.getUsername(), user.getId() != null ? user.getId().value() : null));
        } catch (Exception ignored) {
            // Best-effort like login audit.
        }
    }
}
