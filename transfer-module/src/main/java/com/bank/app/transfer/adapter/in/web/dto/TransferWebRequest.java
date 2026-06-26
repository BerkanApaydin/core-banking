package com.bank.app.transfer.adapter.in.web.dto;

import com.bank.app.common.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransferWebRequest(
        @NotBlank(message = "{validation.sender.iban.required}") @Pattern(regexp = "^TR[0-9]{24}$", message = "{validation.sender.iban.invalid}") String senderIban,
        @NotBlank(message = "{validation.receiver.iban.required}") @Pattern(regexp = "^TR[0-9]{24}$", message = "{validation.receiver.iban.invalid}") String receiverIban,
        @NotNull(message = "{validation.amount.required}") @Positive(message = "{validation.amount.positive}") BigDecimal amount,
        @NotNull(message = "{validation.currency.required}") Currency currency) {
}
