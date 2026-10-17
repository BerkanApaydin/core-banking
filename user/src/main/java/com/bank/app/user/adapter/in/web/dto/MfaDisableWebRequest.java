package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** MFA removal: re-authentication password plus a current TOTP code. */
public record MfaDisableWebRequest(
    @NotBlank(message = "{validation.password.required}")
    @Size(max = 72, message = "{validation.password.tooLong}") String password,
    @NotBlank(message = "{validation.totp.invalid}")
    @Pattern(regexp = "\\d{6,8}", message = "{validation.totp.invalid}") String code
) {
}
