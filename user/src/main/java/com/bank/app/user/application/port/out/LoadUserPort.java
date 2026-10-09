package com.bank.app.user.application.port.out;

import com.bank.app.user.domain.User;
import java.util.Optional;

public interface LoadUserPort {
    Optional<User> findByUsername(String username);

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
}
