package com.bank.app.common.domain;

import java.util.Objects;

public record UserId(Long value) {
    public UserId {
        Objects.requireNonNull(value, "Kullanıcı ID null olamaz");
        if (value <= 0) {
            throw new IllegalArgumentException("Kullanıcı ID pozitif olmalıdır: " + value);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
