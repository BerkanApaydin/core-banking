package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    private final PasswordPolicy policy = PasswordPolicy.DEFAULT;

    @Test
    void shouldAcceptValidPassword() {
        List<String> errors = policy.validate("Password1");
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldRejectPasswordShorterThanMinLength() {
        List<String> errors = policy.validate("Ab1");
        assertTrue(errors.stream().anyMatch(e -> e.contains("en az")));
    }

    @Test
    void shouldRejectPasswordWithoutUppercase() {
        List<String> errors = policy.validate("password1");
        assertTrue(errors.stream().anyMatch(e -> e.contains("büyük harf")));
    }

    @Test
    void shouldRejectPasswordWithoutLowercase() {
        List<String> errors = policy.validate("PASSWORD1");
        assertTrue(errors.stream().anyMatch(e -> e.contains("küçük harf")));
    }

    @Test
    void shouldRejectPasswordWithoutDigit() {
        List<String> errors = policy.validate("Password");
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    @Test
    void shouldRejectNullPassword() {
        List<String> errors = policy.validate(null);
        assertTrue(errors.stream().anyMatch(e -> e.contains("boş")));
    }

    @Test
    void shouldRejectBlankPassword() {
        List<String> errors = policy.validate("   ");
        assertTrue(errors.stream().anyMatch(e -> e.contains("boş")));
    }

    @Test
    void shouldReturnMultipleErrorsForWeakPassword() {
        List<String> errors = policy.validate("short");
        assertTrue(errors.size() >= 2);
        assertTrue(errors.stream().anyMatch(e -> e.contains("en az")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    @Test
    void shouldAcceptPasswordWithExactMinLength() {
        List<String> errors = policy.validate("Abcdef1g");
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldAcceptPasswordWithAllRequirementsMet() {
        List<String> errors = policy.validate("Str0ng!Pass#");
        assertTrue(errors.isEmpty());
    }
}
