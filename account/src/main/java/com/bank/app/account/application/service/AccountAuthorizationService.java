package com.bank.app.account.application.service;

import com.bank.app.account.domain.Account;
import com.bank.app.common.application.service.ResourceOwnershipPolicy;
import com.bank.app.common.domain.exception.AuthorizationException;

public class AccountAuthorizationService {

    private final ResourceOwnershipPolicy ownershipPolicy;

    public AccountAuthorizationService(ResourceOwnershipPolicy ownershipPolicy) {
        this.ownershipPolicy = ownershipPolicy;
    }

    public void authorizeAccountOwner(Account account, String errorMessage) {
        ownershipPolicy.requireOwner(account.getUserId().value(), errorMessage);
    }

    /** Queries hide the existence of accounts owned by another user. */
    public boolean isCurrentUserOwner(Account account) {
        return account.getUserId().value().equals(getCurrentUserId());
    }

    public void authorizeUserAction(Long expectedUserId, String errorMessage) {
        ownershipPolicy.requireOwner(expectedUserId, errorMessage);
    }

    public Long getCurrentUserId() {
        return ownershipPolicy.currentUserIdOrThrow(new AuthorizationException("error.login_required", null,
                "You must be logged in to perform this action."));
    }

    public String getCurrentUsername() {
        return ownershipPolicy.currentUsernameOrSystem();
    }
}
