package com.bank.app.common.adapter.in.security;

import com.bank.app.common.adapter.out.security.CustomUserDetails;
import com.bank.app.common.application.port.out.security.JwtPort;
import com.bank.app.common.application.port.out.security.TokenBlacklistPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.Collections;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtPort jwtPort;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistPort tokenBlacklistPort;

    public JwtAuthenticationFilter(JwtPort jwtPort, UserDetailsService userDetailsService,
            TokenBlacklistPort tokenBlacklistPort) {
        this.jwtPort = jwtPort;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        if (tokenBlacklistPort.isBlacklisted(jwt)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
            return;
        }

        try {
            username = jwtPort.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtPort.extractUserId(jwt);
                String role = jwtPort.extractRole(jwt);
                UserDetails userDetails;

                if (userId != null && role != null) {
                    userDetails = new CustomUserDetails(
                            userId,
                            username,
                            "",
                            Collections.singletonList(
                                    new SimpleGrantedAuthority(role)));
                } else {
                    userDetails = this.userDetailsService.loadUserByUsername(username);
                }

                if (jwtPort.isTokenValid(jwt)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
