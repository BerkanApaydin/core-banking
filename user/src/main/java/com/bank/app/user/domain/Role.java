package com.bank.app.user.domain;

public enum Role {
    ROLE_USER,
    ROLE_ADMIN;

    public static Role fromString(String value) {
        if (value == null || value.isBlank()) {
            return ROLE_USER;
        }
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + value);
        }
    }
}
