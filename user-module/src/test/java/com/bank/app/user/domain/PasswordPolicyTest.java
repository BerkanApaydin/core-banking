package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    private final PasswordPolicy defaultPolicy = PasswordPolicy.DEFAULT;

    @Test
    void shouldAcceptValidPassword() {
        assertTrue(defaultPolicy.validate("Password1").isEmpty());
    }

    @Test
    void shouldAcceptPasswordWithSpecialCharacters() {
        assertTrue(defaultPolicy.validate("Str0ng!Pass#").isEmpty());
    }

    @Test
    void shouldAcceptPasswordAtExactMinLength() {
        assertTrue(defaultPolicy.validate("Abcdef1g").isEmpty());
    }

    // --- single rule violations ---

    @Test
    void shouldRejectNull() {
        List<String> errors = defaultPolicy.validate(null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("boş"));
    }

    @Test
    void shouldRejectBlank() {
        List<String> errors = defaultPolicy.validate("   ");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("boş"));
    }

    @Test
    void shouldRejectEmptyString() {
        List<String> errors = defaultPolicy.validate("");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("boş"));
    }

    @Test
    void shouldFailWhenTooShort() {
        List<String> errors = defaultPolicy.validate("Ab1");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("karakter olmalıdır"));
    }

    @Test
    void shouldFailWhenMissingUppercase() {
        List<String> errors = defaultPolicy.validate("password1");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("büyük harf"));
    }

    @Test
    void shouldFailWhenMissingLowercase() {
        List<String> errors = defaultPolicy.validate("PASSWORD1");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("küçük harf"));
    }

    @Test
    void shouldFailWhenMissingDigit() {
        List<String> errors = defaultPolicy.validate("Password");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("rakam"));
    }

    // --- combined rule violations ---

    @Test
    void shouldFailWhenTooShortAndNoUppercase() {
        List<String> errors = defaultPolicy.validate("ab1");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("karakter olmalıdır")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("büyük harf")));
    }

    @Test
    void shouldFailWhenTooShortAndNoLowercase() {
        List<String> errors = defaultPolicy.validate("AB1");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("karakter olmalıdır")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("küçük harf")));
    }

    @Test
    void shouldFailWhenTooShortAndNoDigit() {
        List<String> errors = defaultPolicy.validate("Abc");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("karakter olmalıdır")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    @Test
    void shouldFailWhenMissingBothCases() {
        List<String> errors = defaultPolicy.validate("12345678");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("büyük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("küçük harf")));
    }

    @Test
    void shouldFailWhenMissingUppercaseAndDigit() {
        List<String> errors = defaultPolicy.validate("password!");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("büyük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    @Test
    void shouldFailWhenMissingLowercaseAndDigit() {
        List<String> errors = defaultPolicy.validate("PASSWORD!");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("küçük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    @Test
    void shouldFailWhenMissingAllRequirements() {
        List<String> errors = defaultPolicy.validate("@$%^&");
        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("karakter olmalıdır")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("büyük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("küçük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    @Test
    void shouldFailAllExceptLengthWhenLengthMetButAllElseMissing() {
        List<String> errors = defaultPolicy.validate("@@@@@@@@");
        assertEquals(3, errors.size());
        assertTrue(errors.stream().noneMatch(e -> e.contains("karakter olmalıdır")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("büyük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("küçük harf")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rakam")));
    }

    // --- custom policy ---

    @Test
    void shouldAcceptAnyNonBlankWhenAllRulesDisabled() {
        PasswordPolicy lenient = new PasswordPolicy(0, false, false, false);
        assertTrue(lenient.validate("a").isEmpty());
        assertTrue(lenient.validate("abc").isEmpty());
    }

    @Test
    void shouldRejectBlankEvenWhenAllRulesDisabled() {
        PasswordPolicy lenient = new PasswordPolicy(0, false, false, false);
        assertFalse(lenient.validate(null).isEmpty());
        assertFalse(lenient.validate("   ").isEmpty());
    }

    @Test
    void shouldApplyCustomMinLength() {
        PasswordPolicy strict = new PasswordPolicy(12, true, true, true);
        List<String> errors = strict.validate("Abcdef1g");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("12"));
    }

    @Test
    void shouldRespectDisabledUppercaseRequirement() {
        PasswordPolicy noUpper = new PasswordPolicy(8, false, true, true);
        assertTrue(noUpper.validate("abcdefg1").isEmpty());
    }

    @Test
    void shouldSkipUppercaseCheckWhenDisabledEvenIfPasswordHasUppercase() {
        PasswordPolicy noUpper = new PasswordPolicy(8, false, true, true);
        assertTrue(noUpper.validate("ABCDEFg1").isEmpty());
    }

    @Test
    void shouldRespectDisabledLowercaseRequirement() {
        PasswordPolicy noLower = new PasswordPolicy(8, true, false, true);
        assertTrue(noLower.validate("ABCDEF1G").isEmpty());
    }

    @Test
    void shouldSkipLowercaseCheckWhenDisabledEvenIfPasswordHasLowercase() {
        PasswordPolicy noLower = new PasswordPolicy(8, true, false, true);
        assertTrue(noLower.validate("abcdef1G").isEmpty());
    }

    @Test
    void shouldRespectDisabledDigitRequirement() {
        PasswordPolicy noDigit = new PasswordPolicy(8, true, true, false);
        assertTrue(noDigit.validate("Abcdefgh").isEmpty());
    }

    @Test
    void shouldSkipDigitCheckWhenDisabledEvenIfPasswordHasDigit() {
        PasswordPolicy noDigit = new PasswordPolicy(8, true, true, false);
        assertTrue(noDigit.validate("Abcdefgh1").isEmpty());
    }
}
