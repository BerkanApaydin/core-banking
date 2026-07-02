package com.bank.app.user.domain;

import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.UserId;
import java.time.LocalDateTime;
import java.util.Objects;

public class User extends BaseAggregateRoot {

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
        this.password = Objects.requireNonNull(password, "Password must not be null");
        this.role = role != null ? role : Role.ROLE_USER;
        this.email = email;
        this.phone = phone;
        this.version = version;
    }

    public static User create(String username, String password) {
        return create(username, password, null, null);
    }

    public static User create(String username, String password, EmailAddress email, PhoneNumber phone) {
        User user = new User(null, username, password, Role.ROLE_USER, email, phone);
        user.registerEvent(new UserRegisteredEvent(
                null, username, Role.ROLE_USER.name(), LocalDateTime.now()));
        return user;
    }

    private static String validateUsername(String username) {
        Objects.requireNonNull(username, "Username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (username.trim().length() > 255) {
            throw new IllegalArgumentException("Username can be at most 255 characters");
        }
        return username.trim();
    }

    public void changePassword(String newEncodedPassword) {
        Objects.requireNonNull(newEncodedPassword, "New password must not be null");
        if (newEncodedPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        this.password = newEncodedPassword;
    }

    public void updateEmail(EmailAddress newEmail) {
        this.email = Objects.requireNonNull(newEmail, "Email must not be null");
    }

    public void updatePhone(PhoneNumber newPhone) {
        this.phone = Objects.requireNonNull(newPhone, "Phone must not be null");
    }

    public void assignRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "Role must not be null");
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
