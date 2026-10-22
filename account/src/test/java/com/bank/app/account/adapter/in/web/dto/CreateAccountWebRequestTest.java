package com.bank.app.account.adapter.in.web.dto;

import com.bank.app.common.domain.BalanceLimits;
import com.bank.app.common.domain.Currency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAccountWebRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void shouldCreateWithValidFields() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet Yilmaz",
                new BigDecimal("1000.00"), Currency.TRY);
        assertThat(request.ownerName()).isEqualTo("Ahmet Yilmaz");
        assertThat(request.initialBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(request.currency()).isEqualTo(Currency.TRY);
    }

    @Test
    void shouldNotExposeClientSelectedUserIdOrIban() {
        assertThat(CreateAccountWebRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("userId", "iban");
    }

    @Test
    void shouldFailValidationWhenOwnerNameBlank() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "",
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.owner.name.required}");
    }

    @Test
    void shouldFailValidationWhenBalanceNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet", null, Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.balance.required}");
    }

    @Test
    void shouldFailValidationWhenCurrencyNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("1000.00"), null);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.currency.required}");
    }

    @Test
    void shouldFailValidationWhenOwnerNameTooLong() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "A".repeat(256),
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        // Exact bundle key: a renamed key would otherwise ship silently as a
        // literal placeholder in the 400 response body.
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.owner.name.tooLong}");
    }

    @Test
    void shouldAcceptOwnerNameOfExactlyMaximumLength() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "A".repeat(255),
                new BigDecimal("1000.00"), Currency.TRY);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldFailValidationWhenBalanceHasTooManyDecimals() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("100.001"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.balance.scale}");
    }

    @Test
    void shouldFailValidationWhenBalanceExceedsMax() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("1000000000.01"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.balance.max}");
    }

    @Test
    void shouldAcceptBalanceExactlyAtTheCeiling() {
        // The ceiling moving with no test failing is the risk: the legal
        // maximum must stay legal.
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal(BalanceLimits.MAX_BALANCE), Currency.TRY);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldFailValidationWhenBalanceIsNegative() {
        // The only field-level guard against a negative opening balance:
        // without it the value reaches the domain, where Money.exact throws a
        // generic IllegalArgumentException (INVALID_ARGUMENT) that names no
        // field — the client is not told which input is wrong.
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("-100.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(v -> v.getMessage())
                .containsExactly("{validation.balance.negative}");
    }

    @Test
    void decimalMaxShouldMirrorSharedBalanceCeiling() throws Exception {
        var annotation = CreateAccountWebRequest.class.getDeclaredField("initialBalance")
                .getAnnotation(DecimalMax.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(BalanceLimits.MAX_BALANCE);
    }
}
