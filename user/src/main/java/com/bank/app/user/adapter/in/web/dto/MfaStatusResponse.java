package com.bank.app.user.adapter.in.web.dto;

/** MFA enrollment status for the authenticated user (drives the UI). */
public record MfaStatusResponse(
    boolean mfaEnabled
) {
}
