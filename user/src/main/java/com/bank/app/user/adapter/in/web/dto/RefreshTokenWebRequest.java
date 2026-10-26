package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenWebRequest(
        // F-7: i18n key like every other DTO (was a hard-coded English
        // literal). SE-4: field cap so one giant token string cannot become
        // a memory-DoS vector before the global body-size filter; real JWTs
        // are a few hundred chars, 4096 leaves 8x headroom.
        @NotBlank(message = "{validation.refreshToken.required}")
        @Size(max = 4096, message = "{validation.refreshToken.tooLong}") String refreshToken) {
}
