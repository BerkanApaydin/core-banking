package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.TransferCancelledNotificationEvent;
import com.bank.app.transfer.domain.TransferCompletedNotificationEvent;
import com.bank.app.transfer.domain.TransferFailedNotificationEvent;

public interface SendNotificationPort {
    void notifyTransferCompleted(TransferCompletedNotificationEvent event);
    void notifyTransferCancelled(TransferCancelledNotificationEvent event);
    void notifyTransferFailed(TransferFailedNotificationEvent event);
}
