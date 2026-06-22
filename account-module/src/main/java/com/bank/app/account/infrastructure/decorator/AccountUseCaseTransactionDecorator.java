package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.common.domain.Money;
import org.springframework.transaction.annotation.Transactional;

public class AccountUseCaseTransactionDecorator implements CreateAccountUseCase, AccountTransferOperationPort {

    private final CreateAccountUseCase createDelegate;
    private final AccountTransferOperationPort transferDelegate;

    public AccountUseCaseTransactionDecorator(CreateAccountUseCase createDelegate, AccountTransferOperationPort transferDelegate) {
        this.createDelegate = createDelegate;
        this.transferDelegate = transferDelegate;
    }

    @Override
    @Transactional
    public AccountResponse execute(CreateAccountRequest request) {
        return createDelegate.execute(request);
    }

    @Override
    @Transactional
    public void executeTransfer(Long senderId, Long receiverId, Money amount) {
        transferDelegate.executeTransfer(senderId, receiverId, amount);
    }

    @Override
    @Transactional
    public void reverseTransfer(Long senderId, Long receiverId, Money amount) {
        transferDelegate.reverseTransfer(senderId, receiverId, amount);
    }
}
