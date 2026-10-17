package com.bank.app.user.application.port.out;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-use password-reset token store. Only digests are persisted
 * (same rule as bearer/refresh tokens); raw tokens travel solely through the
 * out-of-band notification channel.
 */
public interface PasswordResetTokenPort {

    record PasswordResetToken(String tokenHash, Long userId, LocalDateTime expiresAt, LocalDateTime usedAt) {
        public PasswordResetToken {
            Objects.requireNonNull(tokenHash, "Token hash must not be null");
            Objects.requireNonNull(userId, "User ID must not be null");
            Objects.requireNonNull(expiresAt, "Expiry must not be null");
        }

        public boolean isUsableAt(LocalDateTime now) {
            return usedAt == null && expiresAt.isAfter(now);
        }
    }

    void issue(PasswordResetToken token);

    Optional<PasswordResetToken> findByHash(String tokenHash);

    /**
     * Consumes a token exactly once: the UPDATE is conditional on the row
     * still being unused, so two concurrent confirms with the same token
     * cannot both pass (C-1 pattern, same as refresh rotation). Returns
     * {@code true} when this caller won the consumption.
     */
    boolean tryMarkUsed(String tokenHash, LocalDateTime usedAt);

    /** Revokes every outstanding token of a user (rotation + post-reset cleanup). */
    void deleteByUserId(Long userId);
}
