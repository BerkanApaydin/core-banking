package com.bank.app.account.adapter.in.web.dto;

import com.bank.app.common.domain.BalanceLimits;
import com.bank.app.common.domain.Currency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAccountWebRequest(
        @NotBlank(message = "{validation.owner.name.required}") @Size(max = 255, message = "{validation.owner.name.tooLong}") String ownerName,
        @NotNull(message = "{validation.balance.required}") @PositiveOrZero(message = "{validation.balance.negative}") @Digits(integer = 13, fraction = 2, message = "{validation.balance.scale}") @DecimalMax(value = BalanceLimits.MAX_BALANCE, message = "{validation.balance.max}") BigDecimal initialBalance,
        @NotNull(message = "{validation.currency.required}") Currency currency) {
}
