package com.bank.app.common.domain;

/**
 * PII guard for raw IBAN strings in logs, exception messages, audit details
 * and API error responses.
 *
 * <p>{@link Iban#toString()} already masks, but many boundaries carry the raw
 * {@code String} form (URL path variables, map keys, exception arguments).
 * Route every such value through {@link #mask(String)} before it can reach a
 * log line or a client-visible message. Format mirrors
 * {@code Iban.toString()} (first 8 + {@code *******} + last 4) so masked
 * values stay recognizable across layers.
 */
public final class IbanLogMask {

    private IbanLogMask() {
    }

    /**
     * Masks a raw IBAN string. Null-safe: {@code null}, blank and
     * short/invalid values collapse to {@code ***} so a malformed input can
     * never leak through partial masking.
     */
    public static String mask(String iban) {
        String normalized = Iban.normalize(iban);
        if (normalized == null || normalized.length() < 12) {
            return "***";
        }
        return normalized.substring(0, 8) + "*******" + normalized.substring(normalized.length() - 4);
    }

    /** Masks an {@link Iban} value object (delegates to its masked {@code toString}). */
    public static String mask(Iban iban) {
        return iban == null ? "***" : iban.toString();
    }
}
