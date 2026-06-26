package com.bank.app.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("PasswordPolicy")
class PasswordPolicyTest {

    private final PasswordPolicy defaultPolicy = PasswordPolicy.DEFAULT;

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should reject minLength less than 1")
        void shouldRejectMinLengthLessThanOne() {
            assertThatThrownBy(() -> new PasswordPolicy(0, true, true, true))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minLength");
            assertThatThrownBy(() -> new PasswordPolicy(-1, true, true, true))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minLength");
        }
    }

    @Nested
    @DisplayName("valid passwords")
    class ValidPasswords {

        @Test
        @DisplayName("should accept valid password")
        void shouldAcceptValidPassword() {
            assertThat(defaultPolicy.validate("Password1")).isEmpty();
        }

        @Test
        @DisplayName("should accept password with special characters")
        void shouldAcceptPasswordWithSpecialCharacters() {
            assertThat(defaultPolicy.validate("Str0ng!Pass#")).isEmpty();
        }

        @Test
        @DisplayName("should accept password at exact min length")
        void shouldAcceptPasswordAtExactMinLength() {
            assertThat(defaultPolicy.validate("Abcdef1g")).isEmpty();
        }
    }

    @Nested
    @DisplayName("single rule violations")
    class SingleRuleViolations {

        @Test
        @DisplayName("should reject null")
        void shouldRejectNull() {
            List<String> errors = defaultPolicy.validate(null);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("boş");
        }

        @Test
        @DisplayName("should reject blank")
        void shouldRejectBlank() {
            List<String> errors = defaultPolicy.validate("   ");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("boş");
        }

        @Test
        @DisplayName("should reject empty string")
        void shouldRejectEmptyString() {
            List<String> errors = defaultPolicy.validate("");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("boş");
        }

        @Test
        @DisplayName("should fail when too short")
        void shouldFailWhenTooShort() {
            List<String> errors = defaultPolicy.validate("Ab1");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("karakter olmalıdır");
        }

        @Test
        @DisplayName("should fail when missing uppercase")
        void shouldFailWhenMissingUppercase() {
            List<String> errors = defaultPolicy.validate("password1");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("büyük harf");
        }

        @Test
        @DisplayName("should fail when missing lowercase")
        void shouldFailWhenMissingLowercase() {
            List<String> errors = defaultPolicy.validate("PASSWORD1");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("küçük harf");
        }

        @Test
        @DisplayName("should fail when missing digit")
        void shouldFailWhenMissingDigit() {
            List<String> errors = defaultPolicy.validate("Password");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("rakam");
        }
    }

    @Nested
    @DisplayName("combined rule violations")
    class CombinedRuleViolations {

        @Test
        @DisplayName("should fail when too short and no uppercase")
        void shouldFailWhenTooShortAndNoUppercase() {
            List<String> errors = defaultPolicy.validate("ab1");
            assertThat(errors).hasSize(2);
            assertThat(errors).anyMatch(e -> e.contains("karakter olmalıdır"));
            assertThat(errors).anyMatch(e -> e.contains("büyük harf"));
        }

        @Test
        @DisplayName("should fail when too short and no lowercase")
        void shouldFailWhenTooShortAndNoLowercase() {
            List<String> errors = defaultPolicy.validate("AB1");
            assertThat(errors).hasSize(2);
            assertThat(errors).anyMatch(e -> e.contains("karakter olmalıdır"));
            assertThat(errors).anyMatch(e -> e.contains("küçük harf"));
        }

        @Test
        @DisplayName("should fail when too short and no digit")
        void shouldFailWhenTooShortAndNoDigit() {
            List<String> errors = defaultPolicy.validate("Abc");
            assertThat(errors).hasSize(2);
            assertThat(errors).anyMatch(e -> e.contains("karakter olmalıdır"));
            assertThat(errors).anyMatch(e -> e.contains("rakam"));
        }

        @Test
        @DisplayName("should fail when missing both cases")
        void shouldFailWhenMissingBothCases() {
            List<String> errors = defaultPolicy.validate("12345678");
            assertThat(errors).hasSize(2);
            assertThat(errors).anyMatch(e -> e.contains("büyük harf"));
            assertThat(errors).anyMatch(e -> e.contains("küçük harf"));
        }

        @Test
        @DisplayName("should fail when missing uppercase and digit")
        void shouldFailWhenMissingUppercaseAndDigit() {
            List<String> errors = defaultPolicy.validate("password!");
            assertThat(errors).hasSize(2);
            assertThat(errors).anyMatch(e -> e.contains("büyük harf"));
            assertThat(errors).anyMatch(e -> e.contains("rakam"));
        }

        @Test
        @DisplayName("should fail when missing lowercase and digit")
        void shouldFailWhenMissingLowercaseAndDigit() {
            List<String> errors = defaultPolicy.validate("PASSWORD!");
            assertThat(errors).hasSize(2);
            assertThat(errors).anyMatch(e -> e.contains("küçük harf"));
            assertThat(errors).anyMatch(e -> e.contains("rakam"));
        }

        @Test
        @DisplayName("should fail when missing all requirements")
        void shouldFailWhenMissingAllRequirements() {
            List<String> errors = defaultPolicy.validate("@$%^&");
            assertThat(errors).hasSize(4);
            assertThat(errors).anyMatch(e -> e.contains("karakter olmalıdır"));
            assertThat(errors).anyMatch(e -> e.contains("büyük harf"));
            assertThat(errors).anyMatch(e -> e.contains("küçük harf"));
            assertThat(errors).anyMatch(e -> e.contains("rakam"));
        }

        @Test
        @DisplayName("should fail all except length when length met but all else missing")
        void shouldFailAllExceptLengthWhenLengthMetButAllElseMissing() {
            List<String> errors = defaultPolicy.validate("@@@@@@@@");
            assertThat(errors).hasSize(3);
            assertThat(errors).noneMatch(e -> e.contains("karakter olmalıdır"));
            assertThat(errors).anyMatch(e -> e.contains("büyük harf"));
            assertThat(errors).anyMatch(e -> e.contains("küçük harf"));
            assertThat(errors).anyMatch(e -> e.contains("rakam"));
        }
    }

    @Nested
    @DisplayName("custom policy")
    class CustomPolicy {

        @Test
        @DisplayName("should accept any non-blank when all rules disabled")
        void shouldAcceptAnyNonBlankWhenAllRulesDisabled() {
            PasswordPolicy lenient = new PasswordPolicy(1, false, false, false);
            assertThat(lenient.validate("a")).isEmpty();
            assertThat(lenient.validate("abc")).isEmpty();
        }

        @Test
        @DisplayName("should reject blank even when all rules disabled")
        void shouldRejectBlankEvenWhenAllRulesDisabled() {
            PasswordPolicy lenient = new PasswordPolicy(1, false, false, false);
            assertThat(lenient.validate(null)).isNotEmpty();
            assertThat(lenient.validate("   ")).isNotEmpty();
        }

        @Test
        @DisplayName("should apply custom min length")
        void shouldApplyCustomMinLength() {
            PasswordPolicy strict = new PasswordPolicy(12, true, true, true);
            List<String> errors = strict.validate("Abcdef1g");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("12");
        }

        @Test
        @DisplayName("should respect disabled uppercase requirement")
        void shouldRespectDisabledUppercaseRequirement() {
            PasswordPolicy noUpper = new PasswordPolicy(8, false, true, true);
            assertThat(noUpper.validate("abcdefg1")).isEmpty();
        }

        @Test
        @DisplayName("should skip uppercase check when disabled even if password has uppercase")
        void shouldSkipUppercaseCheckWhenDisabledEvenIfPasswordHasUppercase() {
            PasswordPolicy noUpper = new PasswordPolicy(8, false, true, true);
            assertThat(noUpper.validate("ABCDEFg1")).isEmpty();
        }

        @Test
        @DisplayName("should respect disabled lowercase requirement")
        void shouldRespectDisabledLowercaseRequirement() {
            PasswordPolicy noLower = new PasswordPolicy(8, true, false, true);
            assertThat(noLower.validate("ABCDEF1G")).isEmpty();
        }

        @Test
        @DisplayName("should skip lowercase check when disabled even if password has lowercase")
        void shouldSkipLowercaseCheckWhenDisabledEvenIfPasswordHasLowercase() {
            PasswordPolicy noLower = new PasswordPolicy(8, true, false, true);
            assertThat(noLower.validate("abcdef1G")).isEmpty();
        }

        @Test
        @DisplayName("should respect disabled digit requirement")
        void shouldRespectDisabledDigitRequirement() {
            PasswordPolicy noDigit = new PasswordPolicy(8, true, true, false);
            assertThat(noDigit.validate("Abcdefgh")).isEmpty();
        }

        @Test
        @DisplayName("should skip digit check when disabled even if password has digit")
        void shouldSkipDigitCheckWhenDisabledEvenIfPasswordHasDigit() {
            PasswordPolicy noDigit = new PasswordPolicy(8, true, true, false);
            assertThat(noDigit.validate("Abcdefgh1")).isEmpty();
        }
    }
}
