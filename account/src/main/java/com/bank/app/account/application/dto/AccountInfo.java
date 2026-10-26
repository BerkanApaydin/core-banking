package com.bank.app.account.application.dto;

import java.util.Objects;

public record AccountInfo(Long id, Long userId, String currency, String status) {
    public AccountInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(status);
    }
}
