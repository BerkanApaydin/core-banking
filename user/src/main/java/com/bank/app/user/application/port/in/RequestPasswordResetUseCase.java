package com.bank.app.user.application.port.in;

public interface RequestPasswordResetUseCase {
    /**
     * Starts a password reset: mints a single-use token and sends it through
     * the out-of-band notification channel.
     *
     * <p>Lookup is by username only (email is nullable and non-unique, so it
     * cannot identify an account; email-as-username deployments pass the
     * email here and it resolves naturally).
     *
     * <p>Enumeration-safe by contract: unknown usernames produce the exact
     * same (empty) outcome as known ones — no error, no timing shortcut.
     */
    void execute(String username);
}
