package com.bank.app.infrastructure.config;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.out.clock.UtcDateTimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(
        auditorAwareRef = "auditorProvider",
        dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider utcDateTimeProvider(ClockProviderPort clockProviderPort) {
        return new UtcDateTimeProvider(clockProviderPort);
    }

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
