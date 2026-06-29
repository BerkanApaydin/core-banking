package com.bank.app.user.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[\\d\\s.-]{6,20}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "Phone number must not be null");
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Geçersiz telefon numarası formatı: " + value);
        }
    }

    @Override
    public String toString() {
        if (value.length() < 6) return value;
        return value.replaceAll(".(?=.{3})", "*");
    }
}
