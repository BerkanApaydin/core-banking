package com.bank.app.transfer.application.service;

import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.common.application.service.ResourceOwnershipPolicy;
import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.exception.AuthorizationException;

public class TransferAuthorizationService {

    private final AccountAclPort accountAclPort;
    private final ResourceOwnershipPolicy ownershipPolicy;

    public TransferAuthorizationService(AccountAclPort accountAclPort, ResourceOwnershipPolicy ownershipPolicy) {
        this.accountAclPort = accountAclPort;
        this.ownershipPolicy = ownershipPolicy;
    }

    public AccountInfo authorizeSender(String senderIban) {
        AccountInfo senderInfo = accountAclPort.getAccountInfoForTransfer(senderIban);
        ownershipPolicy.requireOwner(senderInfo.userId(),
                "You are not authorized to transfer from this account.");
        return senderInfo;
    }

    public AccountInfo getReceiverInfo(String receiverIban) {
        return accountAclPort.getAccountInfoForTransfer(receiverIban);
    }

    public AccountInfo authorizeByAccountId(AccountId accountId) {
        AccountInfo accountInfo = accountAclPort.getAccountInfo(accountId);
        ownershipPolicy.requireOwner(accountInfo.userId(),
                "You are not authorized to cancel this transfer.");
        return accountInfo;
    }

    public AccountInfo authorizeAccountAccess(AccountId accountId, String errorMessage) {
        AccountInfo accountInfo = accountAclPort.getAccountInfo(accountId);
        ownershipPolicy.requireOwner(accountInfo.userId(), errorMessage);
        return accountInfo;
    }

    public void authorizeTransferAccess(Long senderUserId, Long receiverUserId, String errorMessage) {
        ownershipPolicy.requireParticipant(senderUserId, receiverUserId,
                new AuthorizationException("error.session_not_found", null,
                        "Session not found. Please log in again."),
                new AuthorizationException("error.not_resource_owner", null, errorMessage));
    }

    public String getCurrentUsername() {
        return ownershipPolicy.currentUsernameOrSystem();
    }
}
