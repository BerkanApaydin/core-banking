package com.bank.app.user.domain;

import java.time.Clock;
import java.util.Objects;

/**
 * Test-only factory for transient (unpersisted) users.
 *
 * <p>Replaces the removed {@code User.create(String, String rawPassword)}
 * overloads: every path goes through the production constructors, so a raw
 * (non-BCrypt) password fails fast exactly as in production. Fixtures must
 * supply a BCrypt-shaped hash (the {@code $2a$}/{@code $2b$}/{@code $2y$}
 * prefix check in {@link EncodedPassword#of}).
 */
public final class UserTestFactory {

    /** BCrypt-shaped fixture hash. Never a real secret; prefix-valid only. */
    public static final String FIXTURE_HASH = "$2a$12$testfixturehash000000000000000000000001";

    private UserTestFactory() {}

    public static User newUser(String username) {
        return newUser(username, FIXTURE_HASH);
    }

    public static User newUser(String username, String bcryptHash) {
        Objects.requireNonNull(username, "Username must not be null");
        return User.create(username, EncodedPassword.of(bcryptHash), null, null, Clock.systemUTC());
    }

    public static User newUser(String username, String bcryptHash, EmailAddress email, PhoneNumber phone) {
        Objects.requireNonNull(username, "Username must not be null");
        return User.create(username, EncodedPassword.of(bcryptHash), email, phone, Clock.systemUTC());
    }
}
