package com.bank.app.user.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public class User {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[\\d\\s.-]{6,20}$");

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final String email;
    private final String phone;
    private final Long version;

    public User(Long id, String username, String password, String role) {
        this(id, username, password, role, null, null);
    }

    public User(Long id, String username, String password, String role, String email, String phone) {
        this(id, username, password, role, email, phone, null);
    }

    public User(Long id, String username, String password, String role, String email, String phone, Long version) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "Kullanıcı adı null olamaz");
        this.password = Objects.requireNonNull(password, "Şifre null olamaz");
        this.role = role != null ? role : "ROLE_USER";
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Geçersiz email formatı: " + email);
        }
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Geçersiz telefon numarası formatı: " + phone);
        }
        this.email = email;
        this.phone = phone;
        this.version = version;
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

    public Long getVersion() {
        return version;
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

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role='" + role + "'}";
    }
}
