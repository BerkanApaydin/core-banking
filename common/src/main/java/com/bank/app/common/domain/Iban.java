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
    private static final Pattern TURKISH_BBAN_PATTERN = Pattern.compile("[0-9]{22}");

    public Iban {
        Objects.requireNonNull(value, "IBAN must not be null");
        value = normalize(value);
        if (!DEFAULT_PATTERN.matcher(value).matches()) {
            throw new InvalidIbanException("Invalid IBAN format");
        }
    }

    public static String normalize(String iban) {
        if (iban == null) return null;
        return iban.replaceAll("\\s", "").toUpperCase();
    }

    /** Builds a checksum-valid Turkish IBAN from its 22-digit domestic part. */
    public static Iban fromTurkishBban(String bban) {
        if (bban == null || !TURKISH_BBAN_PATTERN.matcher(bban).matches()) {
            throw new InvalidIbanException("Invalid Turkish BBAN format");
        }
        int checkDigits = 98 - mod97(bban + "292700");
        return new Iban("TR" + (checkDigits < 10 ? "0" : "") + checkDigits + bban);
    }

    /** ISO 13616 MOD 97-10 checksum for a normalized Turkish IBAN. */
    public boolean hasValidChecksum() {
        // TR IBANs contain only digits after the country code. Move the first
        // four characters to the end and expand T/R to 29/27 respectively.
        String rearranged = value.substring(4) + "2927" + value.substring(2, 4);
        return mod97(rearranged) == 1;
    }

    private static int mod97(String digits) {
        int remainder = 0;
        for (int i = 0; i < digits.length(); i++) {
            remainder = (remainder * 10 + (digits.charAt(i) - '0')) % 97;
        }
        return remainder;
    }

    public void requireValidChecksum() {
        if (!hasValidChecksum()) {
            throw new InvalidIbanException("Invalid IBAN check digits: " + this);
        }
    }

    @Override
    public String toString() {
        // Length is always 26 (validated above): no short-value branch needed.
        return value.substring(0, 8) + "*******" + value.substring(value.length() - 4);
    }
}
