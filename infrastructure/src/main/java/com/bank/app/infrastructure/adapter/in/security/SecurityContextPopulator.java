package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.SimpleAuthenticatedPrincipal;
import com.bank.app.user.application.port.out.JwtPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.util.Collections;

/**
 * Establishes (and clears) the request security context plus the log MDC.
 *
 * <p>Extracted from {@code JwtAuthenticationFilter}: signature verification
 * stays in the filter, while context assembly lives here. MDC cleanup is
 * explicit ({@link #clear}) because Tomcat threads are reused and MDC is a
 * ThreadLocal — leaking it would attribute the next request's logs to this
 * user. Callers must invoke {@link #clear} in a {@code finally} block.
 */
final class SecurityContextPopulator {

    static final String MDC_USER_KEY = "userId";

    private SecurityContextPopulator() {
    }

    static void establish(JwtPort.VerifiedToken verified, String role, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UserDetails userDetails = new SimpleAuthenticatedPrincipal(
                verified.userId(),
                verified.username(),
                Collections.singletonList(
                        new SimpleGrantedAuthority(role)));

        // Signature already verified by the caller; establish the security context.
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
        authToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        // MDC userId for downstream logs (prod JSON layout renders it).
        // Removed via clear() below: Tomcat threads are reused and
        // MDC is a ThreadLocal — leaking it would attribute the next
        // request's logs to this user.
        MDC.put(MDC_USER_KEY, String.valueOf(verified.userId()));
    }

    static void clear() {
        MDC.remove(MDC_USER_KEY);
    }
}
