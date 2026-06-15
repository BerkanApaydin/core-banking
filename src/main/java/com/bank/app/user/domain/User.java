package com.bank.app.user.domain;

import java.util.Objects;

public class User {
    private final Long id;
    private final String username;
    private final String password;
    private final String role;

    public User(Long id, String username, String password, String role) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "Kullanıcı adı null olamaz");
        this.password = Objects.requireNonNull(password, "Şifre null olamaz");
        this.role = role != null ? role : "ROLE_USER";
    }

    public static User create(String username, String password) {
        return new User(null, username, password, "ROLE_USER");
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }
}
