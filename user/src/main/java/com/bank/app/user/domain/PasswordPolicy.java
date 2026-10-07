package com.bank.app.user.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigit) {

    public static final PasswordPolicy DEFAULT = new PasswordPolicy(12, true, true, true);

    /**
     * Widely breached passwords (compared case-insensitively). A fixed
     * offline list on purpose: an online breach API would put an external
     * dependency with PII implications on the registration hot path.
     * These entries must stay out of test fixtures (see PasswordPolicyTest).
     */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password12", "password123",
            "password1234", "password12345", "password123456",
            "qwerty", "qwerty1", "qwerty12", "qwerty123", "qwerty12345",
            "qwertyuiop", "1q2w3e4r", "1qaz2wsx",
            "letmein", "letmein1", "letmein12", "letmein123", "letmein1234",
            "welcome", "welcome1", "welcome12", "welcome123", "welcome1234",
            "welcome2024", "welcome2025",
            "admin", "admin1", "admin12", "admin123", "admin12345",
            "administrator", "root12345678",
            "abc123", "abc1234", "abc12345", "abc123456",
            "12345678", "123456789", "1234567890", "1234567",
            "123456a", "a12345678", "password2024", "password2025",
            "monkey123", "monkey1234", "dragon123", "dragon1234",
            "football12", "football123", "sunshine12", "sunshine123",
            "master123", "master1234", "shadow123", "shadow1234",
            "superman12", "superman123", "iloveyou12", "iloveyou123",
            "changeme12", "changeme123", "princess12", "princess123",
            "bank12345678", "banking123", "money123456",
            "trustno1", "trustno12", "passw0rd123");

    public PasswordPolicy {
        if (minLength < 1) {
            throw new IllegalArgumentException("minLength must be at least 1: " + minLength);
        }
    }

    public List<String> validate(String rawPassword) {
        List<String> errors = new ArrayList<>();
        if (rawPassword == null || rawPassword.isBlank()) {
            errors.add("Password must not be empty");
            return errors;
        }
        if (rawPassword.length() < minLength) {
            errors.add("Password must be at least " + minLength + " characters");
        }
        if (requireUppercase && !rawPassword.chars().anyMatch(Character::isUpperCase)) {
            errors.add("Password must contain at least one uppercase letter");
        }
        if (requireLowercase && !rawPassword.chars().anyMatch(Character::isLowerCase)) {
            errors.add("Password must contain at least one lowercase letter");
        }
        if (requireDigit && !rawPassword.chars().anyMatch(Character::isDigit)) {
            errors.add("Password must contain at least one digit");
        }
        if (COMMON_PASSWORDS.contains(rawPassword.toLowerCase(Locale.ROOT))) {
            errors.add("Password is too common, choose a less predictable one");
        }
        return errors;
    }
}
