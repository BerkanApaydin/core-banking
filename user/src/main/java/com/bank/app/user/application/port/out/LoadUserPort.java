package com.bank.app.user.application.port.out;

import com.bank.app.user.domain.User;
import java.util.Optional;

public interface LoadUserPort {
    Optional<User> findByUsername(String username);

    /**
     * R-U12: username lookup that takes a pessimistic row lock for the
     * duration of the current use-case transaction. Password-reset requests
     * use it to serialize "delete the previous live token + insert the new
     * one", which a plain SELECT cannot do (two concurrent requests could
     * both see no token and insert one each).
     *
     * <p>Defaults to the unlocked lookup so test doubles and adapters without
     * row-lock support keep working; the JPA adapter overrides it with
     * {@code SELECT ... FOR UPDATE}.
     */
    default Optional<User> findByUsernameForUpdate(String username) {
        return findByUsername(username);
    }

    /**
     * Primary-key lookup for security re-validation (SEC-01: the access-token
     * filter re-checks the token generation on admin paths). Defaults to
     * empty so external/test doubles that only support username lookup keep
     * working; the JPA adapter overrides it with an indexed PK read.
     */
    default Optional<User> findById(Long userId) {
        return Optional.empty();
    }

    /**
     * AV-2: narrow projection for the admin token-version re-check (SEC-01).
     * Infrastructure's JwtAuthenticationFilter needs only the generation
     * counter, not the full User aggregate — this keeps the platform filter
     * free of {@code user.domain} imports (DIP). Defaults to the findById
     * mapping; adapters may override with a lighter select.
     */
    default Optional<Long> findTokenVersionById(Long userId) {
        return findById(userId).map(User::getTokenVersion);
    }

    /**
     * Narrow projection for the admin MFA gate. Admin paths refuse a
     * password-only session (see {@code AdminMfaValidator}); the account can
     * still log in and enroll through the non-admin MFA endpoints, then the
     * next admin request sees the updated flag. Defaults to the findById
     * mapping; the JPA adapter overrides it with a lighter select.
     */
    default Optional<Boolean> findMfaEnabledById(Long userId) {
        return findById(userId).map(User::isMfaEnabled);
    }
}
