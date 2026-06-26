package com.bank.app.common.application.port.out.security;

import java.util.Optional;

public interface SecurityContextPort {
    Optional<Long> getCurrentUserId();
    Optional<String> getCurrentUsername();
    void checkUserAuthorization(Long resourceUserId, String errorMessage);
}
