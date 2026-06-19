package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.Transfer;

public interface SendNotificationPort {
    void notifyTransferCompleted(Transfer transfer);
}
