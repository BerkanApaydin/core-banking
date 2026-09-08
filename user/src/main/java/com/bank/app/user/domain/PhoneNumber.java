package com.bank.app.user.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[\\d\\s.-]{6,20}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "Phone number must not be null");
        value = value.trim();
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid phone number format: " + value);
        }
        long digitCount = value.chars().filter(Character::isDigit).count();
        if (digitCount < 6) {
            throw new IllegalArgumentException("Invalid phone number format: " + value);
        }
    }

    @Override
    public String toString() {
        // Length is always >= 6: the constructor rejects shorter values.
        return value.replaceAll(".(?=.{3})", "*");
    }
}
