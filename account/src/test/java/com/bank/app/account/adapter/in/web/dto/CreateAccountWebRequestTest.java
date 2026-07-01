package com.bank.app.account.adapter.in.web.dto;

import com.bank.app.common.domain.Currency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
                1L, "TR290006200000000000000123", "Ahmet Yılmaz",
                new BigDecimal("1000.00"), Currency.TRY);
        assertThat(request.userId()).isEqualTo(1L);
        assertThat(request.iban()).isEqualTo("TR290006200000000000000123");
        assertThat(request.ownerName()).isEqualTo("Ahmet Yılmaz");
        assertThat(request.initialBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(request.currency()).isEqualTo(Currency.TRY);
    }

    @Test
    void shouldFailValidationWhenUserIdNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                null, "TR290006200000000000000123", "Ahmet",
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenIbanBlank() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "", "Ahmet", new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenIbanInvalid() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "invalid", "Ahmet", new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenOwnerNameBlank() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "",
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenBalanceNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "Ahmet", null, Currency.TRY);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationWhenCurrencyNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "Ahmet",
                new BigDecimal("1000.00"), null);
        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
