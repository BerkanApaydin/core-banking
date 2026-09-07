package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import java.util.Objects;

public record Iban(String value) {
    private static volatile java.util.regex.Pattern ibanPattern =
            java.util.regex.Pattern.compile("^TR[0-9]{24}$");

    public static synchronized void configurePattern(String regex) {
        Objects.requireNonNull(regex, "IBAN pattern must not be null");
        ibanPattern = java.util.regex.Pattern.compile(regex);
    }

    public Iban {
        Objects.requireNonNull(value, "IBAN must not be null");
        value = normalize(value);
        if (!ibanPattern.matcher(value).matches()) {
            throw new InvalidIbanException("Invalid IBAN format: " + value);
        }
    }

    public static String normalize(String iban) {
        if (iban == null) return null;
        return iban.replaceAll("\\s", "").toUpperCase();
    }

    @Override
    public String toString() {
        if (value.length() < 8) return value;
        return value.substring(0, 8) + "*******" + value.substring(value.length() - 4);
    }
}
