package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.AsyncTransferFailedEvent;

public interface SendNotificationPort {
    void notifyTransferCompleted(AsyncTransferCompletedEvent event);
    void notifyTransferCancelled(AsyncTransferCancelledEvent event);
    void notifyTransferFailed(AsyncTransferFailedEvent event);
}
