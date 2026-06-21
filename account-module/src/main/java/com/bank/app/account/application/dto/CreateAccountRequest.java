package com.bank.app.account.application.dto;

import com.bank.app.common.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @NotNull(message = "{validation.user.id.required}") Long userId,
        @NotBlank(message = "{validation.iban.required}") @Pattern(regexp = "^TR[0-9]{24}$", message = "{validation.iban.invalid}") String iban,
        @NotBlank(message = "{validation.owner.name.required}") String ownerName,
        @NotNull(message = "{validation.balance.required}") @PositiveOrZero(message = "{validation.balance.negative}") BigDecimal initialBalance,
        @NotNull(message = "{validation.currency.required}") Currency currency) {
}
