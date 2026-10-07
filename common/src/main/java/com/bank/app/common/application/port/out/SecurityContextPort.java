package com.bank.app.common.application.port.out;

import java.util.Optional;

public interface SecurityContextPort {
    Optional<Long> getCurrentUserId();
    Optional<String> getCurrentUsername();
    void checkUserAuthorization(Long resourceUserId, String errorMessage);

    /**
     * Exact authority match (for example {@code "ROLE_ADMIN"}). No prefix
     * magic: callers pass the full granted-authority string.
     */
    boolean hasRole(String role);
}
