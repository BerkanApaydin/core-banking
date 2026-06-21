package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.usecase.AccountTransferOperationUseCase;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AccountTransferOperationDecorator implements AccountTransferOperationPort {

    private final AccountTransferOperationUseCase delegate;

    public AccountTransferOperationDecorator(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            SecurityContextPort securityContextPort) {
        this.delegate = new AccountTransferOperationUseCase(loadAccountPort, saveAccountPort, securityContextPort);
    }

    @Override
    public void executeTransfer(Long senderId, Long receiverId, Money amount) {
        delegate.executeTransfer(senderId, receiverId, amount);
    }

    @Override
    public void reverseTransfer(Long senderId, Long receiverId, Money amount) {
        delegate.reverseTransfer(senderId, receiverId, amount);
    }
}
