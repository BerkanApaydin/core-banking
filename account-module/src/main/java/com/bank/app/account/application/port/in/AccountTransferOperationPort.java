package com.bank.app.account.application.port.in;

import com.bank.app.common.domain.Money;

public interface AccountTransferOperationPort {
    void executeTransfer(Long senderId, Long receiverId, Money amount);
    void reverseTransfer(Long senderId, Long receiverId, Money amount);
}
