package com.bank.app.account.domain;

import com.bank.app.account.exception.InvalidIbanException;
import java.util.Objects;
import java.util.regex.Pattern;

public record Iban(String value) {
    private static final Pattern IBAN_PATTERN =
        Pattern.compile("^TR[0-9]{24}$");

    public Iban {
        Objects.requireNonNull(value, "IBAN boş olamaz");
        value = value.replaceAll("\\s", "").toUpperCase();
        if (!IBAN_PATTERN.matcher(value).matches()) {
            throw new InvalidIbanException("Geçersiz IBAN formatı: " + value);
        }
    }
}
