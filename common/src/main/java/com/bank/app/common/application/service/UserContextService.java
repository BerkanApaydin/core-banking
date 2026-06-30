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

    public void checkUserAuthorization(Long resourceUserId, String errorMessage) {
        securityContextPort.checkUserAuthorization(resourceUserId, errorMessage);
    }
}
