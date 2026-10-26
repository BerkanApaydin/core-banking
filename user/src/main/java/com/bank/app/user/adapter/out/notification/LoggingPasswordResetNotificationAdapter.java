package com.bank.app.user.adapter.out.notification;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.port.out.PasswordResetNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulation reset channel (dev/test only): logs a SHA-256 digest prefix for
 * correlation instead of delivering anything — the raw token is
 * password-equivalent and never reaches logs (or any channel), so a local
 * run cannot complete the reset UI flow without a real provider. Dispatched
 * synchronously from the request use case, so a future real provider
 * (email/SMS) only replaces this bean — the single-use/expiring/
 * hashed-at-rest contract stays unchanged. Integration tests drive the
 * confirm path by issuing a token directly through
 * {@code PasswordResetTokenPort} (see MfaFlowIntegrationTest).
 */
@Component
public class LoggingPasswordResetNotificationAdapter implements PasswordResetNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotificationAdapter.class);

    @Override
    public void sendPasswordResetToken(String username, String contact, String token) {
        // Raw tokens are password-equivalent: never persist them to logs.
        // Only the digest prefix is logged for correlation; delivery of the
        // raw token is the real provider's job (this bean is dev/test only).
        String digestPrefix;
        try {
            String digest = token == null ? null : TokenDigest.sha256Hex(token);
            digestPrefix = digest == null ? "null" : digest.substring(0, 12) + "...";
        } catch (RuntimeException e) {
            digestPrefix = "unavailable";
        }
        log.info("Password reset token issued for user '{}' (contact '{}', digest '{}')",
                username, mask(contact), digestPrefix);
    }

    private static String mask(String contact) {
        if (contact == null || contact.length() <= 3) {
            return "***";
        }
        return contact.charAt(0) + "***" + contact.charAt(contact.length() - 1);
    }
}
