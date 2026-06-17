package com.bank.app.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bank.app.common.security.port.SecurityContextPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityUtils implements SecurityContextPort {

    @Override
    public Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() 
                && !(auth instanceof AnonymousAuthenticationToken)
                && !auth.getName().equals("anonymousUser")) {
            Object principal = auth.getPrincipal();
            if (principal instanceof CustomUserDetails customUserDetails) {
                return Optional.of(customUserDetails.getId());
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
            return Optional.of(auth.getName());
        }
        return Optional.empty();
    }

    @Override
    public void checkUserAuthorization(Long resourceUserId, String errorMessage) {
        Long currentUserId = getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Oturum bulunamadı."));
        if (!currentUserId.equals(resourceUserId)) {
            throw new AccessDeniedException(errorMessage);
        }
    }
}
