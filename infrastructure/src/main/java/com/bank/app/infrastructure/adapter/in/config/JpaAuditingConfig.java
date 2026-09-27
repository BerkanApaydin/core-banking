package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() ||
                    authentication instanceof AnonymousAuthenticationToken ||
                    "anonymousUser".equals(authentication.getName())) {
                // Canonical "system" default lives in UserContextService
                // (getCurrentUsernameOrSystem); this audit path cannot use it
                // (no SecurityContextPort here), so the literal stays with an
                // explicit pointer instead of a silent copy.
                return Optional.of("system");
            }
            return Optional.of(Optional.ofNullable(authentication.getName()).orElse("system"));
        };
    }
}
