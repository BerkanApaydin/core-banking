package com.bank.app.transfer.adapter.in.web.dto;

import com.bank.app.common.domain.Currency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransferWebRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldCreateWithValidFields() {
        TransferWebRequest request = new TransferWebRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("500.00"), Currency.TRY);
        assertEquals("TR290006200000000000000111", request.senderIban());
        assertEquals("TR290006200000000000000222", request.receiverIban());
        assertEquals(new BigDecimal("500.00"), request.amount());
        assertEquals(Currency.TRY, request.currency());
    }

    @Test
    void shouldFailValidationWhenSenderIbanBlank() {
        TransferWebRequest request = new TransferWebRequest(
                "", "TR290006200000000000000222",
                new BigDecimal("500.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenSenderIbanInvalid() {
        TransferWebRequest request = new TransferWebRequest(
                "invalid", "TR290006200000000000000222",
                new BigDecimal("500.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenReceiverIbanBlank() {
        TransferWebRequest request = new TransferWebRequest(
                "TR290006200000000000000111", "",
                new BigDecimal("500.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAmountNull() {
        TransferWebRequest request = new TransferWebRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                null, Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAmountNonPositive() {
        TransferWebRequest request = new TransferWebRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("-50.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenCurrencyNull() {
        TransferWebRequest request = new TransferWebRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("500.00"), null);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
