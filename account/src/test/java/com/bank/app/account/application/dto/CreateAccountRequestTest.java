package com.bank.app.account.application.dto;

import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class CreateAccountRequestTest {

    @Test
    void shouldCreateWithAllFields() {
        CreateAccountRequest request = new CreateAccountRequest(
                1L, "TR290006200000000000000111", "Ahmet Yılmaz",
                new BigDecimal("1000.00"), Currency.TRY);

        assertThat(request.userId()).isEqualTo(1L);
        assertThat(request.iban()).isEqualTo("TR290006200000000000000111");
        assertThat(request.ownerName()).isEqualTo("Ahmet Yılmaz");
        assertThat(request.initialBalance()).isEqualByComparingTo("1000.00");
        assertThat(request.currency()).isEqualTo(Currency.TRY);
    }

    @Test
    void shouldHandleZeroBalance() {
        CreateAccountRequest request = new CreateAccountRequest(
                1L, "TR290006200000000000000111", "Ahmet",
                BigDecimal.ZERO, Currency.USD);

        assertThat(request.initialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldHandleAllCurrencyTypes() {
        for (Currency currency : Currency.values()) {
            CreateAccountRequest request = new CreateAccountRequest(
                    1L, "TR290006200000000000000111", "Test",
                    new BigDecimal("100.00"), currency);
            assertThat(request.currency()).isEqualTo(currency);
        }
    }
}
