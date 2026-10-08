package com.bank.app.infrastructure.adapter.out.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import com.bank.app.common.application.port.out.SecurityContextPort;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the current user through the framework-free {@link AuthenticatedPrincipalPort}
 * abstraction, so this adapter never depends on a bounded context's concrete
 * adapter classes — only on ports and the shared kernel (DIP).
 */
@Component
public class SecurityContextAdapter implements SecurityContextPort {

    @Override
    public Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && !auth.getName().equals("anonymousUser")) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AuthenticatedPrincipalPort authenticatedPrincipal) {
                return Optional.of(authenticatedPrincipal.getAuthenticatedUserId());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && !auth.getName().equals("anonymousUser")) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AuthenticatedPrincipalPort authenticatedPrincipal) {
                return Optional.of(authenticatedPrincipal.getAuthenticatedUsername());
            }
            return Optional.of(auth.getName());
        }
        return Optional.empty();
    }

    @Override
    public void checkUserAuthorization(Long resourceUserId, String errorMessage) {
        Long currentUserId = getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("error.session_not_found", null,
                        "Session not found. Please log in again."));
        if (!currentUserId.equals(resourceUserId)) {
            throw new AuthorizationException("error.not_resource_owner", null, errorMessage);
        }
    }

    @Override
    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(auth.getName())) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
