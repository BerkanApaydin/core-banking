package com.bank.app.user.domain;

import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.UserId;
import java.time.Clock;
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
    /**
     * Token generation counter (V39): embedded in issued JWTs as the
     * {@code ver} claim. Role/password changes bump it, which retires every
     * outstanding session at its next refresh (see RefreshSessionUseCaseImpl).
     */
    private long tokenVersion;

    public User(UserId id, String username, String password, Role role) {
        this(id, username, password, role, null, null);
    }

    public User(UserId id, String username, String password, Role role, EmailAddress email, PhoneNumber phone) {
        this(id, username, password, role, email, phone, null);
    }

    /**
     * @param encodedPassword BCrypt-hashed password, NEVER raw input. Raw passwords
     *                        must pass {@code PasswordPolicy.validate} BEFORE encoding
     *                        (see {@code RegisterUserUseCaseImpl}); the domain cannot
     *                        validate policy rules against a hash.
     */
    public User(UserId id, String username, String encodedPassword, Role role, EmailAddress email, PhoneNumber phone, Long version) {
        this(id, username, encodedPassword, role, email, phone, version, 0L);
    }

    public User(UserId id, String username, String encodedPassword, Role role, EmailAddress email,
                PhoneNumber phone, Long version, long tokenVersion) {
        this.id = id;
        this.username = validateUsername(username);
        this.password = Objects.requireNonNull(encodedPassword, "Password must not be null");
        this.role = role != null ? role : Role.ROLE_USER;
        this.email = email;
        this.phone = phone;
        this.version = version;
        if (tokenVersion < 0) {
            throw new IllegalArgumentException("Token version must not be negative");
        }
        this.tokenVersion = tokenVersion;
    }

    public static User create(String username, String password) {
        return create(username, password, null, null, Clock.systemUTC());
    }

    public static User create(String username, String password, EmailAddress email, PhoneNumber phone) {
        return create(username, password, email, phone, Clock.systemUTC());
    }

    public static User create(String username, String password, EmailAddress email, PhoneNumber phone, Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        return new User(null, username, password, Role.ROLE_USER, email, phone);
    }

    public void recordRegistration(Clock clock) {
        if (id == null) {
            throw new IllegalStateException("Registration event requires a persisted user ID");
        }
        registerEvent(new UserRegisteredEvent(
                id.value().toString(), username, role.name(), LocalDateTime.now(clock)));
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
        // A new secret retires every outstanding session at its next refresh.
        this.tokenVersion++;
    }

    public void updateEmail(EmailAddress newEmail) {
        this.email = Objects.requireNonNull(newEmail, "Email must not be null");
    }

    public void updatePhone(PhoneNumber newPhone) {
        this.phone = Objects.requireNonNull(newPhone, "Phone must not be null");
    }

    /**
     * Changes the role and retires outstanding sessions: the bumped
     * {@code tokenVersion} is persisted with the user, and refresh tokens
     * minted before the bump are rejected at rotation time. Wiring this
     * method into a production flow is now safe (token versioning exists);
     * the architecture rule still bans callers outside this class until an
     * admin role-management endpoint lands.
     */
    public void assignRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "Role must not be null");
        this.tokenVersion++;
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

    public long getTokenVersion() {
        return tokenVersion;
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
