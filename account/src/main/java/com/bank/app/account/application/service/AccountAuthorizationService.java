package com.bank.app.account.application.service;

import com.bank.app.account.domain.Account;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;

public class AccountAuthorizationService {

    private final UserContextService userContextService;

    public AccountAuthorizationService(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    public void authorizeAccountOwner(Account account, String errorMessage) {
        userContextService.checkUserAuthorization(account.getUserId().value(), errorMessage);
    }

    public void authorizeUserAction(Long expectedUserId, String errorMessage) {
        userContextService.checkUserAuthorization(expectedUserId, errorMessage);
    }

    public Long getCurrentUserId() {
        return userContextService.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("You must be logged in to perform this action."));
    }
}
