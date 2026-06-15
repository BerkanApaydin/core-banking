package com.bank.app.transfer.application.dto;

import com.bank.app.common.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import jakarta.validation.constraints.Pattern;

public record TransferRequest(
    @NotBlank(message = "Gönderen IBAN boş olamaz")
    @Pattern(regexp = "^TR[0-9]{24}$", message = "Geçersiz gönderen IBAN formatı")
    String senderIban,

    @NotBlank(message = "Alıcı IBAN boş olamaz")
    @Pattern(regexp = "^TR[0-9]{24}$", message = "Geçersiz alıcı IBAN formatı")
    String receiverIban,

    @NotNull(message = "Tutar boş olamaz")
    @Positive(message = "Tutar sıfırdan büyük olmalıdır")
    BigDecimal amount,

    @NotNull(message = "Para birimi boş olamaz")
    Money.Currency currency
) {}
