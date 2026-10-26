package com.bank.app.transfer.adapter.in.web.dto;

import com.bank.app.common.domain.BalanceLimits;
import com.bank.app.common.domain.Currency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PlaceTransferWebRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldCreateWithValidFields() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111",
                "TR870006200000000000000222",
                new BigDecimal("500.00"), Currency.TRY);
        assertEquals("TR770006200000000000000111", request.senderIban());
        assertEquals("TR870006200000000000000222", request.receiverIban());
        assertEquals(new BigDecimal("500.00"), request.amount());
        assertEquals(Currency.TRY, request.currency());
    }

    @Test
    void shouldFailValidationWhenSenderIbanBlank() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "", "TR870006200000000000000222",
                new BigDecimal("500.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenSenderIbanInvalid() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "invalid", "TR870006200000000000000222",
                new BigDecimal("500.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenReceiverIbanBlank() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111", "",
                new BigDecimal("500.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAmountNull() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111",
                "TR870006200000000000000222",
                null, Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAmountNonPositive() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111",
                "TR870006200000000000000222",
                new BigDecimal("-50.00"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenCurrencyNull() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111",
                "TR870006200000000000000222",
                new BigDecimal("500.00"), null);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAmountHasTooManyDecimals() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111",
                "TR870006200000000000000222",
                new BigDecimal("10.001"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAmountExceedsMax() {
        PlaceTransferWebRequest request = new PlaceTransferWebRequest(
                "TR770006200000000000000111",
                "TR870006200000000000000222",
                new BigDecimal("1000000000.01"), Currency.TRY);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void decimalMaxShouldMirrorSharedBalanceCeiling() throws Exception {
        var annotation = PlaceTransferWebRequest.class.getDeclaredField("amount")
                .getAnnotation(DecimalMax.class);
        assertNotNull(annotation);
        assertEquals(BalanceLimits.MAX_BALANCE, annotation.value());
    }
}
