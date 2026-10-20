package com.bank.app.user.application.port.out;

/**
 * Brute-force guard for the MFA code-verification endpoints
 * ({@code /api/v1/auth/mfa/confirm}, {@code /api/v1/auth/mfa/disable}).
 *
 * <p>Why a separate port instead of {@link LoginAttemptPort}: the login
 * guard is IP-keyed and its threshold is tuned for password guessing, while
 * the MFA endpoints are session-authenticated and their budget must be
 * keyed on the ACCOUNT. An IP-keyed budget lets one abusive client lock a
 * legitimate user out of their own second factor; an account-keyed budget
 * confines the damage to the attacker's own account.
 *
 * <p>The port is deliberately store-agnostic: the production implementation
 * is Redis (shared across replicas), the dev/test implementation is Caffeine
 * (single JVM). Which one is wired follows
 * {@code app.security.failed-login.backend} so a deployment cannot end up
 * with a per-JVM MFA budget while its other security counters are shared.
 */
public interface MfaAttemptPort {

    /**
     * Whether the account is currently locked out after too many failed MFA
     * code attempts.
     */
    boolean isBlocked(Long userId);

    /** Records one failed MFA code attempt against the account. */
    void recordFailure(Long userId);

    /** Clears the account's failure counter after a successful verification. */
    void reset(Long userId);

    /** The lockout window in minutes (for the client-facing message). */
    int getWindowMinutes();
}
