package com.bank.app.account.application.dto;

import com.bank.app.common.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
    @NotNull(message = "Kullanıcı ID boş olamaz") Long userId,
    @NotBlank(message = "IBAN boş olamaz") @Pattern(regexp = "^TR[0-9]{24}$", message = "Geçersiz IBAN formatı") String iban,
    @NotBlank(message = "Hesap sahibi adı boş olamaz") String ownerName,
    @NotNull(message = "Başlangıç bakiyesi boş olamaz") @PositiveOrZero(message = "Bakiye negatif olamaz") BigDecimal initialBalance,
    @NotNull(message = "Para birimi boş olamaz") Money.Currency currency
) {}
