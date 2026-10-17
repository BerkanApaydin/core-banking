package com.bank.app.user.adapter.in.web.dto;

import com.bank.app.common.domain.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password-reset request body. Accepts a username (email-as-username
 * deployments pass the email here): the use case resolves by username only
 * and stays enumeration-safe for unknown values.
 */
public record PasswordResetRequestWebRequest(
    @NotBlank(message = "{validation.username.required}")
    @Size(max = FieldLimits.MAX_TEXT_LENGTH, message = "{validation.username.tooLong}") String username
) {
}
