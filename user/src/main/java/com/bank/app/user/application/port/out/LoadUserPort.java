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
}
