package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.user.application.port.out.LoadUserPort;

/**
 * Requires a second factor for admin sessions.
 *
 * <p>An admin account without MFA can still log in and use ordinary banking
 * endpoints, but every admin path (including runtime log-level changes) is
 * refused until enrollment is confirmed. The check reads the current DB flag
 * on each admin request, so enabling MFA takes effect immediately without
 * waiting for the access token to expire.
 *
 * <p>A missing/deleted user fails closed. Kept separate from
 * {@link AdminTokenVersionValidator} because the two checks answer different
 * questions and the projections are independently testable.
 */
class AdminMfaValidator {

    private final LoadUserPort loadUserPort;

    AdminMfaValidator(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    boolean isMfaEnabled(Long userId) {
        return loadUserPort.findMfaEnabledById(userId).orElse(false);
    }
}
