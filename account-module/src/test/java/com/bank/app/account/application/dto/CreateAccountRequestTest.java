package com.bank.app.account.application.dto;

import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreateAccountRequestTest {

    @Test
    void shouldCreateWithAllFields() {
        CreateAccountRequest request = new CreateAccountRequest(
                1L, "TR290006200000000000000111", "Ahmet Yılmaz",
                new BigDecimal("1000.00"), Currency.TRY);

        assertEquals(1L, request.userId());
        assertEquals("TR290006200000000000000111", request.iban());
        assertEquals("Ahmet Yılmaz", request.ownerName());
        assertEquals(0, new BigDecimal("1000.00").compareTo(request.initialBalance()));
        assertEquals(Currency.TRY, request.currency());
    }

    @Test
    void shouldHandleZeroBalance() {
        CreateAccountRequest request = new CreateAccountRequest(
                1L, "TR290006200000000000000111", "Ahmet",
                BigDecimal.ZERO, Currency.USD);

        assertEquals(0, BigDecimal.ZERO.compareTo(request.initialBalance()));
    }

    @Test
    void shouldHandleAllCurrencyTypes() {
        for (Currency currency : Currency.values()) {
            CreateAccountRequest request = new CreateAccountRequest(
                    1L, "TR290006200000000000000111", "Test",
                    new BigDecimal("100.00"), currency);
            assertEquals(currency, request.currency());
        }
    }
}
