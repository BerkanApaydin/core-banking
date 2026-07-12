package com.bank.app.transfer.application.service;

import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;

public class TransferAuthorizationService {

    private final AccountAclPort accountAclPort;
    private final UserContextService userContextService;

    public TransferAuthorizationService(AccountAclPort accountAclPort, UserContextService userContextService) {
        this.accountAclPort = accountAclPort;
        this.userContextService = userContextService;
    }

    public AccountInfo authorizeSender(String senderIban) {
        AccountInfo senderInfo = accountAclPort.getAccountInfoForTransfer(senderIban);
        userContextService.checkUserAuthorization(senderInfo.userId(),
                "You are not authorized to transfer from this account.");
        return senderInfo;
    }

    public AccountInfo getReceiverInfo(String receiverIban) {
        return accountAclPort.getAccountInfoForTransfer(receiverIban);
    }

    public AccountInfo authorizeByAccountId(Long accountId) {
        AccountInfo accountInfo = accountAclPort.getAccountInfo(accountId);
        userContextService.checkUserAuthorization(accountInfo.userId(),
                "You are not authorized to cancel this transfer.");
        return accountInfo;
    }

    public AccountInfo authorizeAccountAccess(Long accountId, String errorMessage) {
        AccountInfo accountInfo = accountAclPort.getAccountInfo(accountId);
        userContextService.checkUserAuthorization(accountInfo.userId(), errorMessage);
        return accountInfo;
    }

    public void authorizeTransferAccess(Long senderUserId, Long receiverUserId, String errorMessage) {
        Long currentUserId = userContextService.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("Session not found."));
        if (!currentUserId.equals(senderUserId) && !currentUserId.equals(receiverUserId)) {
            throw new AuthorizationException(errorMessage);
        }
    }
}
