package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** MFA enrollment confirmation: password re-authentication plus a TOTP code. */
public record MfaConfirmWebRequest(
    @NotBlank(message = "{validation.password.required}")
    @Size(max = 72, message = "{validation.password.tooLong}") String password,
    @NotBlank(message = "{validation.totp.invalid}")
    @Pattern(regexp = "\\d{6,8}", message = "{validation.totp.invalid}") String code
) {
}
