package com.bank.app.user.domain;

import java.time.Clock;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * TOTP verification policy (domain view of {@code MfaProperties}): step size,
 * code length and accepted clock-skew window. Built from configuration at the
 * composition root, so the application layer never depends on config records
 * (see {@code PasswordPolicy} / {@code PasswordPolicyProperties}).
 */
public record TotpPolicy(
        int periodSeconds,
        int digits,
        int allowedSkewWindows
) {
    public TotpPolicy {
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("TOTP period must be positive");
        }
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("TOTP digits must be 6-8");
        }
        if (allowedSkewWindows < 0 || allowedSkewWindows > 2) {
            throw new IllegalArgumentException("TOTP skew windows must be 0-2");
        }
    }

    public boolean verify(byte[] secret, String code, Clock clock) {
        Objects.requireNonNull(secret, "Secret must not be null");
        Objects.requireNonNull(clock, "Clock must not be null");
        return Totp.verify(secret, code, clock, periodSeconds, digits, allowedSkewWindows);
    }

    /**
     * Verifies a code and returns the TOTP step counter that matched, so the
     * caller can persist it and reject a replay of the same code (see
     * {@code users.mfa_last_totp_counter}, V56).
     *
     * @return the matched step counter, or empty when the code is invalid
     */
    public OptionalLong verifyCounter(byte[] secret, String code, Clock clock) {
        Objects.requireNonNull(secret, "Secret must not be null");
        Objects.requireNonNull(clock, "Clock must not be null");
        return Totp.verifyWithCounter(secret, code, clock, periodSeconds, digits, allowedSkewWindows);
    }
}
