package com.bank.app.user.domain;

import com.bank.app.common.domain.UserId;
import java.util.Objects;

public class User {

    private final UserId id;
    private final String username;
    private String password;
    private Role role;
    private EmailAddress email;
    private PhoneNumber phone;
    private final Long version;

    public User(UserId id, String username, String password, Role role) {
        this(id, username, password, role, null, null);
    }

    public User(UserId id, String username, String password, Role role, EmailAddress email, PhoneNumber phone) {
        this(id, username, password, role, email, phone, null);
    }

    public User(UserId id, String username, String password, Role role, EmailAddress email, PhoneNumber phone, Long version) {
        this.id = id;
        this.username = validateUsername(username);
        this.password = Objects.requireNonNull(password, "Şifre null olamaz");
        this.role = role != null ? role : Role.ROLE_USER;
        this.email = email;
        this.phone = phone;
        this.version = version;
    }

    public static User create(String username, String password) {
        return new User(null, username, password, Role.ROLE_USER, null, null);
    }

    public static User create(String username, String password, EmailAddress email, PhoneNumber phone) {
        return new User(null, username, password, Role.ROLE_USER, email, phone);
    }

    private static String validateUsername(String username) {
        Objects.requireNonNull(username, "Kullanıcı adı null olamaz");
        if (username.isBlank()) {
            throw new IllegalArgumentException("Kullanıcı adı boş olamaz");
        }
        if (username.trim().length() > 255) {
            throw new IllegalArgumentException("Kullanıcı adı en fazla 255 karakter olabilir");
        }
        return username.trim();
    }

    public void changePassword(String newEncodedPassword) {
        Objects.requireNonNull(newEncodedPassword, "Yeni şifre null olamaz");
        if (newEncodedPassword.isBlank()) {
            throw new IllegalArgumentException("Şifre boş olamaz");
        }
        this.password = newEncodedPassword;
    }

    public void updateEmail(EmailAddress newEmail) {
        this.email = Objects.requireNonNull(newEmail, "Email null olamaz");
    }

    public void updatePhone(PhoneNumber newPhone) {
        this.phone = Objects.requireNonNull(newPhone, "Telefon null olamaz");
    }

    public void assignRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "Rol null olamaz");
    }

    public boolean hasRole(Role requiredRole) {
        return this.role == requiredRole;
    }

    public UserId getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public EmailAddress getEmail() {
        return email;
    }

    public PhoneNumber getPhone() {
        return phone;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role='" + role + "'}";
    }
}
