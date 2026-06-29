package com.bank.app.transfer.domain;

import java.util.Objects;

public record TransferId(Long value) {
    public TransferId {
        Objects.requireNonNull(value, "Transfer ID must not be null");
    }
}
