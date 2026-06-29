package com.bank.app.user.domain;

import java.util.ArrayList;
import java.util.List;

public record PasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigit) {

    public static final PasswordPolicy DEFAULT = new PasswordPolicy(8, true, true, true);

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
        return errors;
    }
}
