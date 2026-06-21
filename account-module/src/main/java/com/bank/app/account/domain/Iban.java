package com.bank.app.account.domain;

import com.bank.app.account.domain.exception.InvalidIbanException;
import java.util.Objects;
import java.util.regex.Pattern;

public record Iban(String value) {
    private static final Pattern IBAN_PATTERN =
        Pattern.compile("^TR[0-9]{24}$");

    public Iban {
        Objects.requireNonNull(value, "IBAN boş olamaz");
        value = normalize(value);
        if (!IBAN_PATTERN.matcher(value).matches()) {
            throw new InvalidIbanException("Geçersiz IBAN formatı: " + value);
        }
    }

    public static String normalize(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase();
    }
}
