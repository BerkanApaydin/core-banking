package com.bank.app.transfer.application.port;

import com.bank.app.transfer.domain.Transfer;

public interface SendNotificationPort {
    void notifyTransferCompleted(Transfer transfer);
}
