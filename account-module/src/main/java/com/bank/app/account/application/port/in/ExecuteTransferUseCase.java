package com.bank.app.account.application.port.in;

import com.bank.app.common.domain.Money;

public interface ExecuteTransferUseCase {
    void execute(Long senderId, Long receiverId, Money amount);
}
