package com.bank.app.user.application.port.out;

public interface PasswordEncoderPort {
    String encode(String rawPassword);

    /**
     * SE-5: BCrypt comparison seam. Used for the login timing-oracle guard:
     * unknown usernames burn one hash comparison against a dummy hash so the
     * unknown-user path costs the same ~250ms as a wrong-password path.
     */
    boolean matches(String rawPassword, String encodedPassword);
}
