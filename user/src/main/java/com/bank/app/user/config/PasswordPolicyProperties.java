package com.bank.app.user.config;

import com.bank.app.user.domain.PasswordPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security.password")
public record PasswordPolicyProperties(
        @DefaultValue("12") int minLength,
        @DefaultValue("true") boolean requireUppercase,
        @DefaultValue("true") boolean requireLowercase,
        @DefaultValue("true") boolean requireDigit
) {
    public PasswordPolicy toDomain() {
        // Fail fast on insecure or unusable configuration instead of silently
        // accepting weak passwords (organization standard is 12, above the
        // NIST SP 800-63B minimum of 8) or passwords BCrypt cannot represent
        // (72-byte cap, mirrored by AuthWebRequest).
        if (minLength < 12 || minLength > 72) {
            throw new IllegalArgumentException(
                    "Password policy minLength must be between 12 and 72: " + minLength);
        }
        return new PasswordPolicy(minLength, requireUppercase, requireLowercase, requireDigit);
    }
}
