package com.bank.app.user.application.service;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.InvalidMfaCodeException;
import com.bank.app.user.domain.exception.TooManyFailedMfaAttemptsException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Password step-up shared by the MFA management endpoints (enable, confirm,
 * disable).
 *
 * <p>R-U08: these endpoints run inside an authenticated session, but the
 * session alone must not be enough to enrol, confirm or remove a second
 * factor. The password is verified here and wrong guesses are charged to the
 * SAME account-keyed budget that bounds TOTP guesses ({@link MfaAttemptPort}),
 * so the two factors cannot be brute-forced through different endpoints.
 *
 * <p>Failures are deliberately uniform with a wrong TOTP code
 * ({@link InvalidMfaCodeException}, 400): the response must not reveal which
 * factor was wrong. A wrong password is audited as
 * {@link AuditEvent#MFA_REAUTH_FAILED}; audit is best-effort so an audit-store
 * outage cannot hide the rate-limit decision.
 */
public class MfaReauthenticationService {

    private static final Logger log = LoggerFactory.getLogger(MfaReauthenticationService.class);

    private final AuthenticationPort authenticationPort;
    private final MfaAttemptPort mfaAttemptPort;
    private final AuditEventPort auditEventPort;
    private final ClockProviderPort clockProvider;

    public MfaReauthenticationService(AuthenticationPort authenticationPort,
            MfaAttemptPort mfaAttemptPort, AuditEventPort auditEventPort,
            ClockProviderPort clockProvider) {
        this.authenticationPort = authenticationPort;
        this.mfaAttemptPort = mfaAttemptPort;
        this.auditEventPort = auditEventPort;
        this.clockProvider = clockProvider;
    }

    /**
     * Verifies the account password as a step-up for an MFA management
     * operation.
     *
     * @param userId    account whose budget is charged
     * @param username  verified username
     * @param password  presented password (never logged or audited)
     * @param operation human-readable operation for the audit trail
     *                  (for example {@code "MFA disable"})
     * @throws TooManyFailedMfaAttemptsException when the shared account
     *         budget is exhausted
     * @throws InvalidMfaCodeException when the password is wrong
     */
    public void reauthenticate(Long userId, String username, String password, String operation) {
        if (mfaAttemptPort.isBlocked(userId)) {
            throw new TooManyFailedMfaAttemptsException(
                    "Please try again in " + mfaAttemptPort.getWindowMinutes() + " minutes.");
        }
        try {
            authenticationPort.authenticate(username, password);
        } catch (AuthenticationFailedException wrongPassword) {
            // Count first: if the counter store is down, fail closed (503)
            // instead of returning a 400 that was never budgeted.
            mfaAttemptPort.recordFailure(userId);
            auditFailure(userId, username, operation);
            throw new InvalidMfaCodeException();
        }
    }

    /**
     * Clears the shared budget after a step-up that has no second factor to
     * verify (enrolment start). Confirmation and removal must NOT call this
     * after the password: the subsequent code verification owns the reset,
     * otherwise every request with a correct password would erase its own
     * failed-code history.
     */
    public void resetAttempts(Long userId) {
        mfaAttemptPort.reset(userId);
    }

    private void auditFailure(Long userId, String username, String operation) {
        try {
            auditEventPort.publishBestEffort(new AuditEvent(AuditEvent.MFA_REAUTH_FAILED,
                    operation + " re-authentication failed.",
                    LocalDateTime.now(clockProvider.clock()), username, userId));
        } catch (Exception auditFailure) {
            log.warn("MFA re-authentication audit write failed: failureType={}",
                    auditFailure.getClass().getSimpleName());
        }
    }
}
