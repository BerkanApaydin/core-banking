package com.bank.app.user.domain;

import java.util.ArrayList;
import java.util.List;

public record PasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigit) {

    public static final PasswordPolicy DEFAULT = new PasswordPolicy(8, true, true, true);

    public List<String> validate(String rawPassword) {
        List<String> errors = new ArrayList<>();
        if (rawPassword == null || rawPassword.isBlank()) {
            errors.add("Şifre boş olamaz");
            return errors;
        }
        if (rawPassword.length() < minLength) {
            errors.add("Şifre en az " + minLength + " karakter olmalıdır");
        }
        if (requireUppercase && !rawPassword.chars().anyMatch(Character::isUpperCase)) {
            errors.add("Şifre en az bir büyük harf içermelidir");
        }
        if (requireLowercase && !rawPassword.chars().anyMatch(Character::isLowerCase)) {
            errors.add("Şifre en az bir küçük harf içermelidir");
        }
        if (requireDigit && !rawPassword.chars().anyMatch(Character::isDigit)) {
            errors.add("Şifre en az bir rakam içermelidir");
        }
        return errors;
    }
}
