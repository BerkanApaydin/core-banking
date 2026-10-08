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
    }

    @Test
    void shouldFailValidationWhenBalanceNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet", null, Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenCurrencyNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("1000.00"), null);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenOwnerNameTooLong() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "A".repeat(256),
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenBalanceHasTooManyDecimals() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("100.001"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenBalanceExceedsMax() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                "Ahmet",
                new BigDecimal("1000000000.01"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void decimalMaxShouldMirrorSharedBalanceCeiling() throws Exception {
        var annotation = CreateAccountWebRequest.class.getDeclaredField("initialBalance")
                .getAnnotation(DecimalMax.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(BalanceLimits.MAX_BALANCE);
    }
}
