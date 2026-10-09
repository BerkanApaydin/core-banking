package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;

import java.util.Optional;

/**
 * Re-validates the token generation for high-blast-radius requests.
 *
 * <p>SEC-01: a demoted/suspended admin's pre-change access token would
 * otherwise stay valid until expiry. Admin calls are rare, so one indexed PK
 * lookup per admin request is negligible — and it closes the 15-minute
 * residual-authorization window exactly where the blast radius is largest
 * (suspend, audit, user admin).
 *
 * <p>Extracted from {@code JwtAuthenticationFilter} so the comparison logic
 * is directly unit-testable.
 */
class AdminTokenVersionValidator {

    private final JwtPort jwtPort;
    private final LoadUserPort loadUserPort;

    AdminTokenVersionValidator(JwtPort jwtPort, LoadUserPort loadUserPort) {
        this.jwtPort = jwtPort;
        this.loadUserPort = loadUserPort;
    }

    /**
     * Compares the token's {@code ver} claim against the user's current
     * generation. Pre-versioning tokens present 0 and pre-versioning users
     * persist 0 (V39 backfill), so rolling deploys never lock admins out. A
     * deleted user fails closed (empty lookup rejects).
     *
     * <p>AV-2: uses the narrow {@code findTokenVersionById} projection so
     * this platform filter never imports the user BC's domain aggregate.
     */
    boolean hasCurrentTokenVersion(String jwt, Long userId) {
        final long presented = jwtPort.extractTokenVersion(jwt);
        final Optional<Long> current = loadUserPort.findTokenVersionById(userId);
        return current.map(version -> version == presented).orElse(false);
    }
}
