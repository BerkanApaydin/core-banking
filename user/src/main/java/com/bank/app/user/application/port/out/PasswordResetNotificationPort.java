package com.bank.app.user.application.port.out;

/**
 * Out-of-band delivery for password-reset tokens (email/SMS). The simulation
 * implementation logs the token like the transfer notification adapters;
 * a production deployment replaces this bean with a real provider — the
 * use-case contract (single-use, expiring, hashed-at-rest) stays unchanged.
 */
public interface PasswordResetNotificationPort {
    void sendPasswordResetToken(String username, String contact, String token);
}
