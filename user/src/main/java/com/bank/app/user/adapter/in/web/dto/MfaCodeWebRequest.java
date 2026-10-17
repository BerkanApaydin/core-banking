package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** TOTP code body for MFA confirm (enrollment completion). */
public record MfaCodeWebRequest(
    @NotBlank(message = "{validation.totp.invalid}")
    @Pattern(regexp = "\\d{6,8}", message = "{validation.totp.invalid}") String code
) {
}
