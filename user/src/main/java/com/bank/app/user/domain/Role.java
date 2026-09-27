package com.bank.app.user.domain;

public enum Role {
    ROLE_USER,
    ROLE_ADMIN;

    /**
     * Lenient parser for persisted values: null/blank historical rows default
     * to {@code ROLE_USER}. Never bind this to user-controlled input.
     */
    public static Role fromString(String value) {
        if (value == null || value.isBlank()) {
            return ROLE_USER;
        }
        return require(value);
    }

    /**
     * Fail-closed parser for any current or future user-controlled input
     * (request DTOs, admin APIs). Blank and unknown values throw instead of
     * silently degrading to {@code ROLE_USER}.
     */
    public static Role require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + value);
        }
    }
}
