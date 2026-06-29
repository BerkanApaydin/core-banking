package com.bank.app.account.domain;

import java.util.Objects;

public record AccountId(Long value) {
    public AccountId {
        Objects.requireNonNull(value, "Account ID must not be null");
    }
}
