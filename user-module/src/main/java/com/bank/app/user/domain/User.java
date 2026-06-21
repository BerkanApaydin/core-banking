package com.bank.app.user.domain;

import java.util.Objects;

public class User {
    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final String email;
    private final String phone;

    public User(Long id, String username, String password, String role) {
        this(id, username, password, role, null, null);
    }

    public User(Long id, String username, String password, String role, String email, String phone) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "Kullanıcı adı null olamaz");
        this.password = Objects.requireNonNull(password, "Şifre null olamaz");
        this.role = role != null ? role : "ROLE_USER";
        this.email = email;
        this.phone = phone;
    }

    public static User create(String username, String password) {
        return new User(null, username, password, "ROLE_USER", null, null);
    }

    public static User create(String username, String password, String email, String phone) {
        return new User(null, username, password, "ROLE_USER", email, phone);
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

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return username.equalsIgnoreCase(other.username);
    }

    @Override
    public int hashCode() {
        return username.toLowerCase().hashCode();
    }
}
