package com.bank.app.account.application.dto;

import java.util.Objects;

/**
 * Account read model returned by account use cases and the persistence port.
 * Named {@code AccountView} to avoid colliding with transfer's ACL-owned
 * {@code AccountAclPort.AccountInfo}.
 */
public record AccountView(Long id, Long userId, String currency, String status) {
    public AccountView {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(status);
    }
}
