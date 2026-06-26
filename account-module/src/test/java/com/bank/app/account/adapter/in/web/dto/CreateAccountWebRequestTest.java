package com.bank.app.account.adapter.in.web.dto;

import com.bank.app.common.domain.Currency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreateAccountWebRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldCreateWithValidFields() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "Ahmet Yılmaz",
                new BigDecimal("1000.00"), Currency.TRY);
        assertEquals(1L, request.userId());
        assertEquals("TR290006200000000000000123", request.iban());
        assertEquals("Ahmet Yılmaz", request.ownerName());
        assertEquals(new BigDecimal("1000.00"), request.initialBalance());
        assertEquals(Currency.TRY, request.currency());
    }

    @Test
    void shouldFailValidationWhenUserIdNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                null, "TR290006200000000000000123", "Ahmet",
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenIbanBlank() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "", "Ahmet", new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenIbanInvalid() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "invalid", "Ahmet", new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenOwnerNameBlank() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "",
                new BigDecimal("1000.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenBalanceNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "Ahmet", null, Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenCurrencyNull() {
        CreateAccountWebRequest request = new CreateAccountWebRequest(
                1L, "TR290006200000000000000123", "Ahmet",
                new BigDecimal("1000.00"), null);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
