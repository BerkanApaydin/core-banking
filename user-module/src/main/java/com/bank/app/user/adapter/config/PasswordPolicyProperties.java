package com.bank.app.user.adapter.config;

import com.bank.app.user.domain.PasswordPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security.password")
public record PasswordPolicyProperties(
        @DefaultValue("8") int minLength,
        @DefaultValue("true") boolean requireUppercase,
        @DefaultValue("true") boolean requireLowercase,
        @DefaultValue("true") boolean requireDigit
) {
    public PasswordPolicy toDomain() {
        return new PasswordPolicy(minLength, requireUppercase, requireLowercase, requireDigit);
    }
}
