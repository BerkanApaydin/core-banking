package com.bank.app.user.adapter.in.web.dto;

import com.bank.app.common.domain.FieldLimits;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthWebRequest(
    @NotBlank(message = "{validation.username.required}")
    @Size(max = FieldLimits.MAX_TEXT_LENGTH, message = "{validation.username.tooLong}") String username,
    @NotBlank(message = "{validation.password.required}")
    @Size(max = 72, message = "{validation.password.tooLong}") String password,
    @Email(message = "{validation.email.invalid}")
    @Size(max = 254, message = "{validation.email.tooLong}") String email,
    @Size(max = 20, message = "{validation.phone.tooLong}") String phone,
    // Audit (MFA): second factor for enrolled users. Optional — null/absent
    // fails with the generic authentication message when MFA is on. Digits
    // only (TOTP codes are numeric); length checked against the 6-8 range the
    // domain accepts so malformed input 400s before reaching the use case.
    @Pattern(regexp = "\\d{6,8}", message = "{validation.totp.invalid}") String totpCode
) {
    public AuthWebRequest(String username, String password) {
        this(username, password, null, null, null);
    }

    public AuthWebRequest(String username, String password, String email, String phone) {
        this(username, password, email, phone, null);
    }
}
