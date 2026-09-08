package com.bank.app.transfer.adapter.in.web.dto;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransferWebRequest(
        @NotBlank(message = "{validation.sender.iban.required}") @Pattern(regexp = Iban.DEFAULT_IBAN_REGEX, message = "{validation.sender.iban.invalid}") String senderIban,
        @NotBlank(message = "{validation.receiver.iban.required}") @Pattern(regexp = Iban.DEFAULT_IBAN_REGEX, message = "{validation.receiver.iban.invalid}") String receiverIban,
        @NotNull(message = "{validation.amount.required}") @Positive(message = "{validation.amount.positive}") @Digits(integer = 13, fraction = 2, message = "{validation.amount.scale}") @DecimalMax(value = "1000000000.00", message = "{validation.amount.max}") BigDecimal amount,
        @NotNull(message = "{validation.currency.required}") Currency currency) {
}
