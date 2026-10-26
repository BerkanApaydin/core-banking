package com.bank.app.user.domain;

import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.FieldLimits;
import com.bank.app.common.domain.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

public class User extends BaseAggregateRoot {

    private static final Pattern NEW_USERNAME_PATTERN = Pattern.compile(FieldLimits.USERNAME_REGEX);

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
    /**
     * MFA enrollment flag (V54): when true, login additionally requires a
     * TOTP code verified against the secret stored (encrypted) on the users
     * row. The secret itself never enters the domain — it travels through
     * {@code MfaSecretPort} only.
     */
    private boolean mfaEnabled;

    /**
     * @param encodedPassword BCrypt-hashed password, NEVER raw input. Raw passwords
     *                        must pass {@code PasswordPolicy.validate} BEFORE encoding
     *                        (see {@code RegisterUserUseCaseImpl}); the domain cannot
     *                        validate policy rules against a hash. The BCrypt shape is
     *                        enforced via {@link EncodedPassword#of(String)} — raw input
     *                        fails fast instead of being persisted.
     */
    private User(UserId id, String username, String encodedPassword, Role role, EmailAddress email,
                 PhoneNumber phone, Long version, long tokenVersion) {
        Objects.requireNonNull(encodedPassword, "Password must not be null");
        // BCrypt shape check: rejects raw input, never stores it.
        EncodedPassword.of(encodedPassword);
        this.id = id;
        this.username = validateUsername(username);
        this.password = encodedPassword;
        this.role = role != null ? role : Role.ROLE_USER;
        this.email = email;
        this.phone = phone;
        this.version = version;
        if (tokenVersion < 0) {
            throw new IllegalArgumentException("Token version must not be negative");
        }
        this.tokenVersion = tokenVersion;
    }

    /**
     * Creates a new, not-yet-persisted user. New users always have the default
     * role, no identity/version and a zero token generation.
     */
    public static User create(String username, EncodedPassword encodedPassword, EmailAddress email,
                              PhoneNumber phone, Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        Objects.requireNonNull(encodedPassword, "Encoded password must not be null");
        String validatedUsername = validateNewUsername(username);
        return new User(null, validatedUsername, encodedPassword.value(), Role.ROLE_USER,
                email, phone, null, 0L);
    }

    /**
     * The only entry point for materializing persisted state. The hash is
     * re-validated so a corrupt row cannot materialize an illegal user.
     */
    public static User rehydrate(UserId id, String username, String encodedPassword, Role role) {
        return rehydrate(id, username, encodedPassword, role, null, null, null, 0L);
    }

    public static User rehydrate(UserId id, String username, String encodedPassword, Role role,
                                 EmailAddress email, PhoneNumber phone) {
        return rehydrate(id, username, encodedPassword, role, email, phone, null, 0L);
    }

    public static User rehydrate(UserId id, String username, String encodedPassword, Role role,
                                 EmailAddress email, PhoneNumber phone, Long version) {
        return rehydrate(id, username, encodedPassword, role, email, phone, version, 0L);
    }

    public static User rehydrate(UserId id, String username, String encodedPassword, Role role,
                                 EmailAddress email, PhoneNumber phone, Long version, long tokenVersion) {
        return new User(id, username, encodedPassword, role, email, phone, version, tokenVersion);
    }

    public static User rehydrate(UserId id, String username, EncodedPassword encodedPassword, Role role,
                                 EmailAddress email, PhoneNumber phone, Long version, long tokenVersion) {
        return rehydrate(id, username,
                Objects.requireNonNull(encodedPassword, "Encoded password must not be null").value(),
                role, email, phone, version, tokenVersion);
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
        if (username.trim().length() > FieldLimits.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Username can be at most " + FieldLimits.MAX_TEXT + " characters");
        }
        return username.trim();
    }

    /**
     * New accounts only: legacy rows are rehydrated through the regular
     * constructor so an existing username that predates this rule still loads.
     * The Redis failed-login keys additionally escape glob metacharacters, so
     * legacy names remain safe even though they are no longer issued.
     */
    private static String validateNewUsername(String username) {
        String normalized = validateUsername(username);
        if (!NEW_USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Username may contain only letters, digits, '.', '_' and '-'");
        }
        return normalized;
    }

    /**
     * Replaces the stored hash. {@code newEncodedPassword} must be a BCrypt hash:
     * validated via {@link EncodedPassword#of(String)} (NOT {@code ofTrusted}),
     * so raw input fails fast. Prefer {@link #changePassword(EncodedPassword)}.
     */
    public void changePassword(String newEncodedPassword) {
        Objects.requireNonNull(newEncodedPassword, "New password must not be null");
        if (newEncodedPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        // BCrypt shape check: rejects raw input, never stores it.
        EncodedPassword.of(newEncodedPassword);
        this.password = newEncodedPassword;
        // A new secret retires every outstanding session at its next refresh.
        this.tokenVersion++;
    }

    /**
     * Preferred password change: takes an {@link EncodedPassword}.
     *
     * <p>CS-1: re-validates the shape via {@link EncodedPassword#of(String)}
     * even though the argument is already a VO — a VO built with the legacy
     * {@code ofTrusted} factory (or a future unchecked factory) must never
     * smuggle raw input into the credential store.
     */
    public void changePassword(EncodedPassword next) {
        Objects.requireNonNull(next, "New password must not be null");
        EncodedPassword.of(next.value());
        this.password = next.value();
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

    /**
     * Marks MFA enrollment complete (called after the TOTP code confirms the
     * user actually enrolled the secret). This is an authentication-policy
     * change like a password change, so it bumps {@code tokenVersion} and
     * retires every outstanding session at its next refresh.
     *
     * <p>The use-case layer guards re-confirmation with
     * {@code MfaAlreadyEnabledException} (409), so callers must check
     * {@link #isMfaEnabled()} first instead of relying on idempotence here.
     */
    public void enableMfa() {
        this.mfaEnabled = true;
        this.tokenVersion++;
    }

    /**
     * Removes the second factor (the encrypted secret is deleted through
     * {@code MfaSecretPort} by the same flow). Bumps {@code tokenVersion} for
     * the same reason as {@link #enableMfa()}: an authentication-policy change
     * must retire outstanding sessions at their next refresh.
     */
    public void disableMfa() {
        this.mfaEnabled = false;
        this.tokenVersion++;
    }

    /**
     * Rehydrates the persisted MFA flag without treating it as a policy
     * change. Persistence mappers must use this instead of
     * {@link #enableMfa()}, otherwise every load would bump the token version.
     */
    public void restoreMfaEnabled(boolean enabled) {
        this.mfaEnabled = enabled;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
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
