package com.bank.app.user.application.port.out;

/**
 * Brute-force guard for account-authenticated security operations: TOTP code
 * verification and the password step-up on the MFA management endpoints
 * ({@code /api/v1/auth/mfa/*}).
 *
 * <p>R-U08: password step-up shares this budget instead of getting a
 * separate counter, so the two MFA factors cannot be brute-forced through
 * different endpoints and a wrong password cannot dodge the lockout that
 * bounds wrong codes.
 *
 * <p>Why a separate port instead of {@link LoginAttemptPort}: the login
 * guard is IP-keyed and its threshold is tuned for password guessing, while
 * these endpoints are session-authenticated and their budget must be
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
     * Whether the account is currently locked out after too many failed
     * credential attempts (TOTP code or MFA-management password).
     */
    boolean isBlocked(Long userId);

    /** Records one failed TOTP-code or step-up password attempt. */
    void recordFailure(Long userId);

    /**
     * Clears the account's failure counter after a successful code
     * verification, or after a password-only step-up that has no second
     * factor left to check (enrolment start).
     */
    void reset(Long userId);

    /** The lockout window in minutes (for the client-facing message). */
    int getWindowMinutes();
}
