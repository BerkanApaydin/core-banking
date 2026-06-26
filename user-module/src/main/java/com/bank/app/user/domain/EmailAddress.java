package com.bank.app.user.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public EmailAddress {
        Objects.requireNonNull(value, "Email null olamaz");
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Geçersiz email formatı: " + value);
        }
    }

    @Override
    public String toString() {
        int atIndex = value.indexOf('@');
        if (atIndex <= 1) return value;
        return value.charAt(0) + "***" + value.substring(atIndex - 1);
    }
}
