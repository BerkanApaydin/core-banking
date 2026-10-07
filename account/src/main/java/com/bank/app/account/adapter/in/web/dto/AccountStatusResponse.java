package com.bank.app.account.adapter.in.web.dto;

import java.util.Objects;

/**
 * Admin suspend response: the account's identity and resulting status.
 */
public record AccountStatusResponse(Long accountId, String status) {
    public AccountStatusResponse {
        Objects.requireNonNull(accountId, "Account ID must not be null");
        Objects.requireNonNull(status, "Status must not be null");
    }
}
