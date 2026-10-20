package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** MFA enrollment start: password re-authentication before minting a secret. */
public record MfaEnableWebRequest(
    @NotBlank(message = "{validation.password.required}")
    @Size(max = 72, message = "{validation.password.tooLong}") String password
) {
}
