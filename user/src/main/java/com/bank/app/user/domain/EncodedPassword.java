package com.bank.app.user.domain;

import java.util.Objects;

/**
 * BCrypt-hashed password value object. Prevents accidental use of raw input
 * where a hash is required: {@link #of(String)} rejects anything that does
 * not look like a BCrypt hash, so the {@code User} constructors fail
 * fast instead of persisting a reversible secret.
 *
 * <p>{@link #ofTrusted(String)} wraps hashes read from a trusted store
 * (DB rows written before this type existed, test fixtures) without
 * re-validating their shape — use only for already-persisted values, never
 * for request input.
 */
public final class EncodedPassword {

    private final String hash;

    private EncodedPassword(String hash, boolean trusted) {
        this.hash = Objects.requireNonNull(hash, "Encoded password must not be null");
        if (!trusted) {
            if (hash.isBlank()) {
                throw new IllegalArgumentException("Encoded password must not be empty");
            }
            if (!isBcryptHash(hash)) {
                throw new IllegalArgumentException(
                        "Encoded password must be a BCrypt hash, never raw input");
            }
        }
    }

    public static EncodedPassword of(String hash) {
        return new EncodedPassword(hash, false);
    }

    public static EncodedPassword ofTrusted(String hash) {
        return new EncodedPassword(hash, true);
    }

    private static boolean isBcryptHash(String value) {
        return value.startsWith("$2a$")
                || value.startsWith("$2b$")
                || value.startsWith("$2y$");
    }

    public String value() {
        return hash;
    }

    public String hash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncodedPassword other)) return false;
        return hash.equals(other.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "EncodedPassword{***}";
    }
}
