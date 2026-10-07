package com.bank.app.account.application.port.in;

/**
 * Admin capability: suspends an account so it can neither send nor receive
 * money. Authorization (ROLE_ADMIN) is enforced inside the implementation,
 * following the audit module's pattern, so the rule is unit-testable without
 * a web slice.
 */
public interface SuspendAccountUseCase {

    /**
     * @return the suspended account's current state
     */
    AccountInfo suspend(Long accountId);
}
