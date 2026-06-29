package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;

public interface SendNotificationPort {
    void notifyTransferCompleted(AsyncTransferCompletedEvent event);
}
