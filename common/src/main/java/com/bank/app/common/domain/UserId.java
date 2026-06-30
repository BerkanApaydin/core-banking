package com.bank.app.common.domain;

import java.util.Objects;

public record UserId(Long value) {
    public UserId {
        Objects.requireNonNull(value, "User ID must not be null");
        if (value <= 0) {
            throw new IllegalArgumentException("User ID must be positive: " + value);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
