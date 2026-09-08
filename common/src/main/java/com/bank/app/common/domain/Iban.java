package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import java.util.Objects;
import java.util.regex.Pattern;

public record Iban(String value) {
    /**
     * The single IBAN validation pattern (TR-only). Single source of truth for
     * the web-layer {@code @Pattern} annotations. Immutable by design: a value
     * object must not carry global mutable validation state.
     */
    public static final String DEFAULT_IBAN_REGEX = "^TR[0-9]{24}$";
    private static final Pattern DEFAULT_PATTERN =
            Pattern.compile(DEFAULT_IBAN_REGEX);

    public Iban {
        Objects.requireNonNull(value, "IBAN must not be null");
        value = normalize(value);
        if (!DEFAULT_PATTERN.matcher(value).matches()) {
            throw new InvalidIbanException("Invalid IBAN format: " + value);
        }
    }

    public static String normalize(String iban) {
        if (iban == null) return null;
        return iban.replaceAll("\\s", "").toUpperCase();
    }

    @Override
    public String toString() {
        // Length is always 26 (validated above): no short-value branch needed.
        return value.substring(0, 8) + "*******" + value.substring(value.length() - 4);
    }
}
