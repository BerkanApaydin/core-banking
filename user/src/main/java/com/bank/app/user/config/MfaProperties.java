package com.bank.app.user.config;

import com.bank.app.user.domain.TotpPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * MFA (TOTP) and password-reset tuning, bound from {@code app.security.mfa}.
 * All knobs carry safe defaults; only the secret-encryption key has no usable
 * default by design (see {@code encryptionKeyBase64}).
 */
@Validated
@ConfigurationProperties(prefix = "app.security.mfa")
public record MfaProperties(
        @DefaultValue("clean-architecture-bank") String issuer,
        /**
         * Base64 AES-256 key for TOTP-secret encryption at rest
         * ({@code MFA_ENCRYPTION_KEY}). Blank means "no key configured": the
         * encryptor falls back to an ephemeral in-memory key (dev/test only —
         * secrets become unreadable after restart) and the production startup
         * validator rejects a blank key outright.
         */
        @DefaultValue("") String encryptionKeyBase64,
        @DefaultValue("30") int periodSeconds,
        @DefaultValue("6") int digits,
        @DefaultValue("1") int allowedSkewWindows,
        @DefaultValue("15") int resetTokenValidityMinutes,
        /**
         * Failed MFA code attempts allowed per account before the
         * {@code /api/v1/auth/mfa/*} endpoints are locked out for the
         * window. The budget is ACCOUNT-keyed (not IP-keyed): the endpoints
         * are session-authenticated, so an IP budget would let one abusive
         * client lock a legitimate user out of their own second factor.
         *
         * <p>A negative value is a documented sentinel meaning "guard
         * disabled" — both adapters short-circuit to not-blocked, exactly
         * like the failed-login guard.
         */
        @DefaultValue("5") int mfaMaxAttempts,
        /** Lockout window for failed MFA code attempts, in minutes. */
        @DefaultValue("15") int mfaWindowMinutes,
        /** Memory guard for the Caffeine MFA counter cache. */
        @DefaultValue("10000") int mfaCacheMaxSize
) {
    public MfaProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("MFA issuer must not be blank");
        }
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("MFA period must be positive");
        }
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("MFA digits must be 6-8");
        }
        if (allowedSkewWindows < 0 || allowedSkewWindows > 2) {
            throw new IllegalArgumentException("MFA skew windows must be 0-2");
        }
        if (resetTokenValidityMinutes <= 0 || resetTokenValidityMinutes > 24 * 60) {
            throw new IllegalArgumentException("Reset token validity must be 1-1440 minutes");
        }
        if (mfaWindowMinutes <= 0) {
            throw new IllegalArgumentException("MFA attempt window must be a positive number of minutes");
        }
        if (mfaCacheMaxSize <= 0) {
            throw new IllegalArgumentException("MFA attempt cache size must be positive");
        }
    }

    public TotpPolicy toTotpPolicy() {
        return new TotpPolicy(periodSeconds, digits, allowedSkewWindows);
    }
}
