package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import java.util.Objects;

public record Iban(String value) {
    /**
     * Default validation pattern (TR-only). Single source of truth for the
     * web-layer {@code @Pattern} annotations and the {@code IbanProperties}
     * default — a custom pattern stays runtime-configurable via
     * {@code app.common.iban.pattern}, but the default changes in one place.
     */
    public static final String DEFAULT_IBAN_REGEX = "^TR[0-9]{24}$";
    private static final java.util.regex.Pattern DEFAULT_PATTERN =
            java.util.regex.Pattern.compile(DEFAULT_IBAN_REGEX);
    private static volatile java.util.regex.Pattern ibanPattern = DEFAULT_PATTERN;

    /**
     * Overrides the global IBAN pattern (applied at startup from
     * {@code app.common.iban.pattern}).
     *
     * @deprecated Global mutable validation state on a value object breaks
     * immutability and test isolation. Prefer {@link #Iban(String, java.util.regex.Pattern)}
     * with an explicit pattern. Kept for backward compatibility with existing
     * configuration; will be removed once the pattern is injected.
     */
    @Deprecated(forRemoval = true)
    public static synchronized void configurePattern(String regex) {
        Objects.requireNonNull(regex, "IBAN pattern must not be null");
        if (regex.isBlank()) {
            throw new IllegalArgumentException("IBAN pattern must not be blank");
        }
        ibanPattern = java.util.regex.Pattern.compile(regex);
    }

    /** Creates an IBAN validated against an explicit pattern (no global state). */
    public Iban(String value, java.util.regex.Pattern pattern) {
        this(validateWithPattern(
                Objects.requireNonNull(value, "IBAN must not be null"), pattern));
    }

    private static String validateWithPattern(String value, java.util.regex.Pattern pattern) {
        Objects.requireNonNull(pattern, "IBAN pattern must not be null");
        String normalized = normalize(value);
        if (!pattern.matcher(normalized).matches()) {
            throw new InvalidIbanException("Invalid IBAN format: " + normalized);
        }
        return normalized;
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
