package com.bank.app.common.application.service;

import com.bank.app.common.application.port.out.SecurityContextPort;
import java.util.Optional;

public class UserContextService {

    private final SecurityContextPort securityContextPort;

    public UserContextService(SecurityContextPort securityContextPort) {
        this.securityContextPort = securityContextPort;
    }

    public Optional<Long> getCurrentUserId() {
        return securityContextPort.getCurrentUserId();
    }

    public Optional<String> getCurrentUsername() {
        return securityContextPort.getCurrentUsername();
    }

    /**
     * Username for audit trails and cache keys. Falls back to {@code "system"}
     * for background jobs and legacy events without a user — the single place
     * defining this default (previously copy-pasted in 3 call sites).
     */
    public String getCurrentUsernameOrSystem() {
        return securityContextPort.getCurrentUsername().orElse("system");
    }

    public void checkUserAuthorization(Long resourceUserId, String errorMessage) {
        securityContextPort.checkUserAuthorization(resourceUserId, errorMessage);
    }
}
