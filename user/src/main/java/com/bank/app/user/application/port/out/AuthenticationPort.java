package com.bank.app.user.application.port.out;

import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import java.util.Objects;

public interface AuthenticationPort {
    /** Returns the identity from the credential check, without exposing a password or framework principal. */
    AuthenticatedUser authenticate(String username, String password);

    record AuthenticatedUser(UserId id, String username, Role role, long tokenVersion) {
        public AuthenticatedUser {
            Objects.requireNonNull(id, "Authenticated user ID must not be null");
            Objects.requireNonNull(username, "Authenticated username must not be null");
            Objects.requireNonNull(role, "Authenticated role must not be null");
            if (username.isBlank()) throw new IllegalArgumentException("Authenticated username must not be blank");
            if (tokenVersion < 0) throw new IllegalArgumentException("Token version must not be negative");
        }

        /** Legacy shape: pre-versioning identities carry generation 0. */
        public AuthenticatedUser(UserId id, String username, Role role) {
            this(id, username, role, 0L);
        }
    }
}
