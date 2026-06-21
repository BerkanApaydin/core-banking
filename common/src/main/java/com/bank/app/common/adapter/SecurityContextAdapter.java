package com.bank.app.common.adapter;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bank.app.common.exception.AuthorizationException;
import com.bank.app.common.security.CustomUserDetails;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityContextAdapter implements SecurityContextPort {

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
                .orElseThrow(() -> new AuthorizationException("Oturum bulunamadı."));
        if (!currentUserId.equals(resourceUserId)) {
            throw new AuthorizationException(errorMessage);
        }
    }
}
